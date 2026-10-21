"""Fixed-scope, append-only quality conversion. Probe rolls back; apply requires deployed code proof.

At most fifty executions are committed together. Each portion locks workorders then executions,
checks the shared database run token, and atomically appends declarations, treatment facts and
source-linked node conversions. No reports, states, inventory or external ERP are written.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import uuid
from plan_quality import RULE, canonical, identity, key

DB = 'mes_base_dev_perf_test'
FACTORY = 'TEST_DEV_PERF_20261019'
SYNC = 'legacy-quality-formal'
ACTOR = 100


def quote(value):
    return 'NULL' if value is None else '_utf8mb4 0x' + str(value).encode().hex()


class Client:
    def __init__(self, cnf, database):
        self.process = subprocess.Popen(['mysql', '--defaults-extra-file=' + str(cnf),
            '--batch', '--raw', '--skip-column-names', '--unbuffered', '--skip-reconnect',
            '--default-character-set=utf8mb4', database], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)

    def run(self, sql):
        sentinel = 'END_' + uuid.uuid4().hex
        self.process.stdin.write(sql + "; SELECT '" + sentinel + "';\n")
        self.process.stdin.flush()
        rows = []
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError('SQL failed; transaction closed/rolled back: ' + self.process.stderr.read()[:600])
            line = line.rstrip('\n')
            if line == sentinel:
                return rows
            rows.append(line)

    def number(self, sql):
        rows = self.run(sql)
        assert len(rows) == 1, 'Expected one count'
        return int(rows[0])

    def close(self):
        if self.process.poll() is None:
            try:
                self.run('ROLLBACK')
                self.process.stdin.close()
            except (BrokenPipeError, RuntimeError):
                pass
        self.process.wait()


def insert(table, columns, rows):
    assert rows
    return 'INSERT INTO ' + table + '(' + ','.join(columns) + ') VALUES ' + ','.join(
        '(' + ','.join(row) + ')' for row in rows)


def remaining(client, accepted):
    """Load committed conversion leaves once; group against the frozen plan in memory."""
    from decimal import Decimal
    records = client.run("SELECT JSON_OBJECT('source',a.parent_id,'id',a.id,'sourceHash',a.source_hash,'execution',d.execution_id,'quantity',CAST(d.defect_qty AS CHAR)) FROM mes_legacy_source_mapping a FORCE INDEX(idx_legacy_target) JOIN mes_legacy_migration_batch b ON b.id=a.batch_id AND b.rule_version=a.rule_version AND b.status='COMPLETED' JOIN mes_production_defect d ON d.id=a.entity_id AND d.request_key=a.id WHERE a.entity_kind='QUALITY_DECLARATION' AND a.rule_version='legacy-quality-formal-v1'")
    converted = {r['source']:r for line in records if (r := json.loads(line))}
    records = client.run("SELECT JSON_OBJECT('source',c.parent_id,'id',c.id,'node',c.entity_id,'gross',JSON_EXTRACT(c.conversion_payload,'$.grossDelta'),'loss',JSON_EXTRACT(c.conversion_payload,'$.convertedDefectQty')) FROM mes_legacy_source_mapping c FORCE INDEX(idx_legacy_target) JOIN mes_legacy_source_mapping n ON n.id=c.parent_id AND n.entity_kind='NODE' AND c.source_hash=n.source_hash AND c.entity_id=n.entity_id JOIN mes_legacy_migration_batch b ON b.id=c.batch_id AND b.rule_version=c.rule_version AND b.status='COMPLETED' WHERE c.entity_kind='NODE_QUALITY_CONVERSION' AND c.rule_version='legacy-quality-formal-v1'")
    node_records = {r['source']:r for line in records if (r := json.loads(line))}
    pending, already = [], 0
    for e in accepted:
        count = 0
        for d in e['declarations']:
            q = d['source']
            r = converted.get(q['mappingId'])
            if r is not None:
                assert r['id'] == key(q['mappingId']) and r['sourceHash'] == q['sourceHash'] and r['execution'] == int(e['executionId']) and Decimal(r['quantity']) == Decimal(q['raw']['quantity']), 'Committed declaration mismatch'
                count += 1
        assert count in (0,len(e['declarations'])), 'Partial source conversion requires inspection'
        if count:
            for n in e['nodes']:
                r = node_records[n['mappingId']]
                assert r['id'] == key(n['mappingId']) and r['node'] == n['nodeId'] and Decimal(str(r['gross'])) == Decimal(str(n['gross'])) and Decimal(str(r['loss'])) == Decimal(str(n['loss'])), 'Committed node conversion mismatch'
            already += count
        else:
            pending.append(e)
    return pending, already


def convert(client, portion, snapshot, probe=False, reader=None, start_transaction=True):
    ids = ','.join(str(int(e['executionId'])) for e in portion)
    markers = [key(d['source']['mappingId']) for e in portion for d in e['declarations']]
    markers += [key(n['mappingId']) for e in portion for n in e['nodes']]
    batch = 'LQ-' + hashlib.sha256((snapshot + ':' + ids).encode()).hexdigest()[:32]
    old = client.run('SELECT status FROM mes_legacy_migration_batch WHERE id=' + quote(batch))
    if old:
        assert old == ['COMPLETED'], 'Existing incomplete batch requires inspection'
        actual = client.number('SELECT COUNT(*) FROM mes_legacy_source_mapping WHERE batch_id=' + quote(batch))
        assert actual == len(markers), 'Completed marker count mismatch'
        return dict(batchId=batch, executions=len(portion), declarations=0, replay=True)
    if start_transaction:
        client.run('START TRANSACTION')
    try:
        client.run('SELECT id FROM workorder WHERE id IN (SELECT workorder_id FROM mes_production_execution WHERE id IN (' + ids + ')) ORDER BY id FOR UPDATE')
        locked = client.run("SELECT e.id FROM mes_production_execution e JOIN workorder w ON w.id=e.workorder_id JOIN mes_execution_path p ON p.id=e.id AND p.routing_id=e.routing_id AND p.path_status='LOCKED' WHERE e.id IN (" + ids + ") AND e.status IN ('RUNNING','COMPLETED') AND w.source='LOCAL' AND w.status IN ('RELEASED','COMPLETED') ORDER BY e.id FOR UPDATE")
        assert len(locked) == len(portion), 'State/source/path changed'
        assert client.number('SELECT COUNT(*) FROM mes_production_defect WHERE execution_id IN (' + ids + ')') == 0, 'Existing formal quality requires separate reconciliation'
        from decimal import Decimal
        # Original node and report facts are immutable. Validate the frozen gross/loss
        # at the INSERT SELECT below; unchanged zero-loss nodes need no repeated read.
        before = sorted((int(e['executionId']),int(n['nodeId']),Decimal(str(n['gross'])),Decimal(str(n['loss']))) for e in portion for n in e['nodes'])
        client.run("INSERT INTO mes_legacy_migration_batch(id,rule_version,snapshot_hash,status,approved_scope,operator_user_id,created_at) VALUES(" + ','.join([quote(batch), quote(RULE), quote(snapshot), "'RUNNING'", quote(canonical(markers)), str(ACTOR), 'NOW(6)']) + ')')
        declarations, treatments, mappings = [], [], []
        mapping_columns = ['id','batch_id','parent_id','source_table','source_pk','source_hash','rule_version','entity_kind','entity_id','source_payload','conversion_payload','created_at']
        source_checks = ["(id=" + quote(d['source']['mappingId']) + ' AND parent_id=' + quote(e['parentId']) + ' AND source_hash=' + quote(d['source']['sourceHash']) + ')' for e in portion for d in e['declarations']]
        assert client.number("SELECT COUNT(*) FROM mes_legacy_source_mapping WHERE entity_kind='QUALITY_EVIDENCE' AND (" + ' OR '.join(source_checks) + ')') == len(source_checks), 'Frozen quality identity changed'
        for e in portion:
            for d in e['declarations']:
                q, raw = d['source'], d['source']['raw']
                scrap_proven = d.get('disposition', 'SCRAPPED') == 'SCRAPPED'
                assert scrap_proven == (raw['status'] == '1' and bool(raw.get('scrap_code'))), 'Disposition differs from source'
                raw_note = canonical({k: raw.get(k) for k in ['responsibility_type','responsible_route_id','responsible_process_id','supplier_name']})
                destination_remark = ('历史退废引用 ' + raw['scrap_code'] if scrap_proven else '历史不良尚未处置') + '；未推断当前仓库或物理去向'
                declarations.append([quote(d['declarationId']), '1', "'CREATE'", str(int(e['executionId'])), str(int(d['affectedNodeId'])), str(int(d['affectedNodeId'])), 'NULL' if d['responsibleNodeId'] is None else str(int(d['responsibleNodeId'])), quote(raw['quantity']), quote(d['wasteCategory']), quote(raw['cause']), quote(raw_note[:500]), "'UNSPECIFIED'", quote(destination_remark), quote(key(q['mappingId'])), quote(q['sourceHash']), str(int(d['reporterId'])), quote(raw['create_time']), str(ACTOR), 'NOW(6)'])
                basis = ('迁移核实独立期初损耗：qc_defective/' + q['sourceId'] + '，旧退废引用 ' + raw['scrap_code'] + '；不证明ERP当前有效，不执行库存动作' if scrap_proven else '迁移核实独立期初不良：qc_defective/' + q['sourceId'] + '；原状态未处理，不登记报废或库存动作')
                for action in (['ASSESS','ALLOCATE'] if scrap_proven else ['ASSESS']):
                    treatments.append([quote(identity(q['mappingId'] + ':' + action)), quote(hashlib.sha256((q['sourceHash'] + ':' + action + ':' + str(ACTOR)).encode()).hexdigest()), quote(d['declarationId']), '1', str(int(e['executionId'])), str(int(d['affectedNodeId'])), quote(action), quote('INDEPENDENT') if action == 'ASSESS' else 'NULL', quote(basis), quote(raw['quantity']) if action == 'ALLOCATE' else '0', '0','0','0','0',str(ACTOR),str(ACTOR),'NOW(6)','NOW(6)',quote('本次迁移登记历史质量依据；原发生时间及退废时间保留于来源证据')])
        client.run(insert('mes_production_defect', ['declaration_id','revision_no','record_action','execution_id','affected_node_id','discovered_node_id','responsible_node_id','defect_qty','waste_category','defect_reason','responsibility_remark','destination_type','destination_remark','request_key','request_fingerprint','reported_by','occurred_at','recorded_by','recorded_at'], declarations))
        client.run(insert('mes_quality_treatment', ['id','request_hash','declaration_id','declaration_revision','execution_id','routing_node_id','action_type','attribution_type','basis','scrap_qty','rework_qty','processed_qty','recovered_qty','failed_qty','operator_user_id','confirmed_by','occurred_at','recorded_at','reason'], treatments))
        quality_values, node_values = [], []
        for e in portion:
            for d in e['declarations']:
                q = d['source']
                quality_values.append('SELECT ' + quote(q['mappingId']) + ' original_id,' + quote(key(q['mappingId'])) + ' marker_id,' + quote(canonical({'sourceMappingId':q['mappingId']})) + ' payload,' + quote(canonical({'declarationId':d['declarationId'],'quantity':q['raw']['quantity']})) + ' conversion')
            for n in e['nodes']:
                payload = canonical({'grossDelta':str(n['gross']),'convertedDefectQty':str(n['loss']), 'basis':'FORMAL_DECLARATIONS_REPLACE_OPENING_LOSS'})
                node_values.append('SELECT ' + quote(n['mappingId']) + ' original_id,' + quote(key(n['mappingId'])) + ' marker_id,' + quote(canonical({'sourceNodeMappingId':n['mappingId'],'executionId':int(e['executionId'])})) + ' payload,' + quote(payload) + ' conversion,' + quote(n['loss']) + ' loss,' + quote(n['gross']) + ' gross')
        client.run('INSERT INTO mes_legacy_source_mapping(' + ','.join(mapping_columns) + ') SELECT a.marker_id,' + quote(batch) + ",q.id,'qc_defective',q.source_pk,q.source_hash," + quote(RULE) + ",'QUALITY_DECLARATION',d.id,a.payload,a.conversion,NOW(6) FROM (" + ' UNION ALL '.join(quality_values) + ') a JOIN mes_legacy_source_mapping q ON q.id=a.original_id JOIN mes_production_defect d ON d.request_key=a.marker_id')
        client.run('INSERT INTO mes_legacy_source_mapping(' + ','.join(mapping_columns) + ') SELECT a.marker_id,' + quote(batch) + ",n.id,'LEGACY_NODE_CONVERSION',n.source_pk,n.source_hash," + quote(RULE) + ",'NODE_QUALITY_CONVERSION',n.entity_id,a.payload,a.conversion,NOW(6) FROM (" + ' UNION ALL '.join(node_values) + ") a JOIN mes_legacy_source_mapping n ON n.id=a.original_id WHERE CAST(JSON_EXTRACT(n.conversion_payload,'$.effectiveDefect') AS DECIMAL(20,6))=a.loss AND CAST(JSON_EXTRACT(n.conversion_payload,'$.grossCorrection') AS DECIMAL(20,6))=a.gross")
        actual = client.number('SELECT COUNT(*) FROM mes_legacy_source_mapping WHERE batch_id=' + quote(batch))
        assert actual == len(markers), 'Not all source/node conversions were appended'
        client.run("UPDATE mes_legacy_migration_batch SET status='COMPLETED',finished_at=NOW(6) WHERE id=" + quote(batch) + " AND status='RUNNING'")
        after_sql = "SELECT JSON_OBJECT('execution',JSON_EXTRACT(c.source_payload,'$.executionId'),'node',c.entity_id,'gross',JSON_EXTRACT(c.conversion_payload,'$.grossDelta'),'loss',CAST(d.qty AS CHAR)) FROM mes_legacy_source_mapping c LEFT JOIN (SELECT execution_id,affected_node_id,SUM(defect_qty) qty FROM mes_production_defect WHERE execution_id IN (" + ids + ") GROUP BY execution_id,affected_node_id) d ON d.execution_id=CAST(JSON_EXTRACT(c.source_payload,'$.executionId') AS UNSIGNED) AND d.affected_node_id=c.entity_id WHERE c.batch_id=" + quote(batch) + " AND c.entity_kind='NODE_QUALITY_CONVERSION'"
        after = sorted((int(r['execution']),int(r['node']),Decimal(str(r['gross'])),Decimal(str(r['loss']))) for line in client.run(after_sql) if (r := json.loads(line)))
        assert before == after, 'Converted gross/formal loss invariance failed; rolling back'
        if reader is not None:
            from decimal import Decimal
            mapped = []
            for query in reader.values():
                parts = iter(query['values'])
                sql = query['sql']
                sql = sql.replace('?', '{}').format(*(quote(value) for value in parts))
                projected = [json.loads(line) for line in client.run("SELECT JSON_OBJECT('node',routing_node_id,'gross',CAST(gross_correction AS CHAR),'loss',CAST(effective_defect AS CHAR)) FROM (" + sql + ') real_mapper')]
                mapped.append({x['node']:(Decimal(x['gross']),Decimal(x['loss'])) for x in projected})
            assert mapped[0] == mapped[1], 'Single and batch Mapper disagree'
            for n in portion[0]['nodes']:
                assert mapped[0][n['nodeId']] == (Decimal(str(n['gross'])),Decimal(0)), 'Real Mapper failed to replace opening loss'

        if probe:
            client.run('ROLLBACK')
        return dict(batchId=batch, executions=len(portion), declarations=len(declarations), nodeConversions=sum(len(e['nodes']) for e in portion), allNodeGrossAndLossUnchanged=True, rolledBack=probe, realSingleAndBatchMapperVerified=reader is not None)
    except BaseException:
        if client.process.poll() is None:
            client.run('ROLLBACK')
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cnf', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--deployed-proof', type=Path)
    parser.add_argument('--reader-sql', type=Path)
    parser.add_argument('--batch-size', type=int, default=50)
    parser.add_argument('--expected-source-count', type=int, default=12078)
    args = parser.parse_args()
    with gzip.open(args.evidence / 'plan.json.gz', 'rt') as f:
        content = f.read()
    snapshot = hashlib.sha256(content.encode()).hexdigest()
    assert snapshot == (args.evidence / 'plan.sha256').read_text().strip(), 'Frozen plan changed'
    plan = json.loads(content)
    reader = json.loads(args.reader_sql.read_text()) if args.reader_sql else None
    assert args.expected_source_count > 0
    assert plan['rule'] == RULE and plan['sourceCount'] == args.expected_source_count
    assert 1 <= args.batch_size <= 50
    if args.apply:
        assert args.deployed_proof, 'Apply requires current backend code proof'
        proof = json.loads(args.deployed_proof.read_text())
        assert proof['rule'] == RULE and proof['planSha256'] == snapshot and proof['verifiedNewReader'], 'Invalid deployed proof'
    c, center = Client(args.cnf, DB), Client(args.cnf, 'mes_base_dev')
    token = str(uuid.uuid4())
    owned, done = False, 0
    scope = 'factory_id=' + quote(FACTORY) + ' AND sync_key=' + quote(SYNC)
    owner = scope + ' AND run_token=' + quote(token) + " AND status='RUNNING'"
    try:
        assert c.run('SELECT @@server_uuid') == ['03dbc952-7403-11ef-802c-0242ac130002']
        assert center.number("SELECT COUNT(*) FROM mf_factory f JOIN mf_datasource d USING(datasource_id) WHERE f.factory_id=" + quote(FACTORY) + " AND f.status='ENABLED' AND f.runtime_mode='LOCAL' AND d.datasource_id=9 AND d.status='ENABLED' AND d.revision=2 AND d.db_host='192.168.18.25' AND d.db_port=3306 AND d.db_name=" + quote(DB)) == 1
        assert center.number("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id WHERE u.user_id=100 AND u.status='0' AND u.del_flag='0' AND r.kind='PLATFORM' AND r.status='0'") > 0
        if args.apply:
            center.run('INSERT INTO mes_master_sync(factory_id,sync_key) VALUES(' + quote(FACTORY) + ',' + quote(SYNC) + ') ON DUPLICATE KEY UPDATE id=id')
            # Expired crash claims are recovered using the exact observed old token.
            old = center.run("SELECT run_token FROM mes_master_sync WHERE " + scope + " AND status='RUNNING' AND updated_at<DATE_SUB(NOW(6),INTERVAL 30 MINUTE)")
            if old:
                center.run("UPDATE mes_master_sync SET status='FAILED',finished_at=NOW(6),error_message='Expired append-only migration claim recovered' WHERE " + scope + ' AND run_token=' + quote(old[0]) + " AND status='RUNNING' AND updated_at<DATE_SUB(NOW(6),INTERVAL 30 MINUTE)")
            changed = center.run("UPDATE mes_master_sync SET status='RUNNING',run_token=" + quote(token) + ",started_at=NOW(6),updated_at=NOW(6),finished_at=NULL,processed_count=0,error_message=NULL WHERE " + scope + " AND status<>'RUNNING'; SELECT ROW_COUNT()")
            assert changed == ['1'], 'Migration is already running'
            owned = True
        pending, already = remaining(c, plan['accepted'])
        print(json.dumps({'remainingExecutions':len(pending),'alreadyConvertedDeclarations':already}),flush=True)
        if owned:
            assert center.run('UPDATE mes_master_sync SET processed_count=' + str(already) + ',updated_at=NOW(6) WHERE ' + owner + '; SELECT ROW_COUNT()') == ['1']
        portions = [pending[i:i+args.batch_size] for i in range(0, len(pending), args.batch_size)] if args.apply else [pending[:args.batch_size]]
        portions = [portion for portion in portions if portion]
        assert all(sum(len(e['declarations']) for e in portion) <= 1000 for portion in portions), 'Portion exceeds quality read budget'
        output = args.evidence / ('apply-progress.jsonl' if args.apply else 'probe.jsonl')
        with output.open('a' if args.apply else 'w') as f:
            for portion in portions:
                if owned:
                    assert center.run('UPDATE mes_master_sync SET updated_at=NOW(6) WHERE ' + owner + '; SELECT ROW_COUNT()') == ['1'], 'Lost migration ownership'
                result = convert(c, portion, snapshot, probe=not args.apply, reader=reader if not args.apply else None)
                if args.apply:
                    assert center.number('SELECT COUNT(*) FROM mes_master_sync WHERE ' + owner) == 1, 'Lost migration ownership before commit'
                    c.run('COMMIT')
                done += result['declarations']
                result['totalDeclarationsWrittenThisRun'] = done
                f.write(json.dumps(result) + '\n'); f.flush()
                if owned:
                    assert center.run('UPDATE mes_master_sync SET processed_count=' + str(already + done) + ',updated_at=NOW(6) WHERE ' + owner + '; SELECT ROW_COUNT()') == ['1']
                print(json.dumps(result), flush=True)
        if owned:
            assert center.run("UPDATE mes_master_sync SET status='SUCCEEDED',updated_at=NOW(6),finished_at=NOW(6),last_success_at=NOW(6) WHERE " + owner + '; SELECT ROW_COUNT()') == ['1']
    except BaseException:
        if owned:
            center.run("UPDATE mes_master_sync SET status='FAILED',updated_at=NOW(6),finished_at=NOW(6),error_message='Migration stopped; inspect private evidence and replay committed batches only' WHERE " + owner)
        raise
    finally:
        c.close(); center.close()


if __name__ == '__main__':
    main()
