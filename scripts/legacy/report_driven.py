#!/usr/bin/env python3
"""Read-only legacy conversion planner. This program has no database write mode."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from datetime import datetime, timezone

RULE_VERSION = 'report-driven-local-dry-run-v1'
SOURCE = 'prod_j2eedb'
TARGET = 'mes_base_dev_perf_test'
SERVER = '03dbc952-7403-11ef-802c-0242ac130002'
FACTORY = 'TEST_DEV_PERF_20261019'
# Explicit business allowlist. User authentication secrets are never exported.
SOURCE_TABLES = ('pro_reporting_work', 'qc_defective', 'pro_material', 'pro_buckle',
                 'pro_reporting_work_material',
                 'pro_route', 'pro_route_process', 'pro_route_product', 'pro_process',
                 'pro_route_broken_step', 'md_item', 'qc_defective_user',
                 'qc_defective_responsible_user')


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), default=str)


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def identifier(value):
    if not re.fullmatch(r'[A-Za-z0-9_]{1,64}', value):
        raise ValueError('Invalid metadata identifier')
    return '`' + value + '`'


def write_json(path, value):
    with path.open('x') as out:
        out.write(json.dumps(value, ensure_ascii=False, indent=2, default=str) + '\n')


@dataclass
class LegacySourceMapping:
    source_table: str
    source_pk: str
    source_hash: str
    command_key: str
    target_id: str | None = None
    status: str = 'PLANNED_ONLY'


@dataclass
class LegacyReport:
    raw: dict
    mapping: LegacySourceMapping
    node_candidate: str | None
    issues: list[str] = field(default_factory=list)


@dataclass
class LegacyDefect:
    raw: dict
    mapping: LegacySourceMapping
    status: str = 'PENDING_QUANTITY_SEMANTICS'


@dataclass
class LegacyRouting:
    source_route_id: str
    current_node_ids: list[str]
    observed_process_ids: list[str]
    status: str = 'CURRENT_STRUCTURE_ONLY_NOT_HISTORICAL_VERSION'
    target_id: str | None = None


@dataclass
class LegacyExecution:
    key: tuple
    reports: list[LegacyReport]
    routing: LegacyRouting
    input_qty_candidate: str | None
    input_evidence_ids: list[str]
    reported_by_node: dict
    issues: list[str]
    target_id: str | None = None


@dataclass
class LegacyWorkOrder:
    code: str
    source: str
    raw_source_identities: list[str]
    product_codes: list[str]
    planned_start_at: str | None
    planned_end_at: str | None
    planned_qty_candidate: str | None
    plan_rule: str
    executions: list[LegacyExecution]
    issues: list[str]
    target_id: str | None = None


@dataclass
class LegacyMigrationBatch:
    batch_id: str
    rule_version: str
    snapshot_hash: str
    status: str = 'DRY_RUN_NO_TARGET_WRITES'


def mapping(table, row, pk='record_id'):
    identity = str(row[pk])
    # Identity is independent of source hash and batch: changed source must conflict,
    # rather than becoming a new command. The future DB mapping must enforce this.
    return LegacySourceMapping(table, identity, digest(row),
                               digest([SERVER, SOURCE, table, identity]))


def text(row, name):
    value = row.get(name)
    return None if value is None else str(value)


def number(value):
    value = Decimal(str(value))
    if not value.is_finite():
        raise ValueError('Non-finite quantity')
    return value


def target_decimal(value):
    return value > 0 and value == value.quantize(Decimal('.000001')) and value < Decimal('1e14')


def input_candidate(rows, first_process):
    """Sum unique issue mirrors per material, then min(material / qtyPer).

    This is a labelled historical evidence candidate, never a current stock input.
    Conflicting ratios, mirrors, non-first-node inputs and fractional products fail closed.
    """
    if not rows:
        return None, [], ['NO_INPUT_EVIDENCE']
    totals, ratios, seen = defaultdict(Decimal), {}, set()
    evidence = []
    try:
        for row in rows:
            if text(row, 'process_id') != first_process:
                return None, [], ['INPUT_NOT_FIRST_NODE']
            issue = text(row, 'issue_doc_no')
            if not issue or issue in seen:
                return None, [], ['DUPLICATE_OR_MISSING_ISSUE_IDENTITY']
            seen.add(issue)
            detail = json.loads(row['detail'], parse_float=Decimal, parse_int=Decimal)
            if not isinstance(detail, list) or not detail:
                raise ValueError('Missing material detail')
            material_seen = set()
            for item in detail:
                code = item.get('itemCode')
                qty, ratio = number(item['acceptedQty']), number(item['qtyPer'])
                if not code or code in material_seen or qty < 0 or ratio <= 0:
                    raise ValueError('Invalid material detail')
                material_seen.add(code)
                if code in ratios and ratios[code] != ratio:
                    return None, [], ['INPUT_RATIO_CHANGED']
                totals[code] += qty
                ratios[code] = ratio
            evidence.append(text(row, 'record_id'))
        quantity = min(totals[k] / ratios[k] for k in totals)
        if not target_decimal(quantity):
            return None, evidence, ['INPUT_QTY_NOT_TARGET_DECIMAL']
        return str(quantity), evidence, []
    except (ValueError, KeyError, TypeError, InvalidOperation, ZeroDivisionError):
        return None, evidence, ['INVALID_INPUT_JSON_OR_QUANTITY']


def convert(data):
    reports = data['source:pro_reporting_work']
    orders = defaultdict(list)
    for row in reports:
        orders[text(row, 'workorder_code')].append(row)
    route_rows = {text(r, 'route_id'): r for r in data['source:pro_route']}
    nodes = defaultdict(list)
    for row in data['source:pro_route_process']:
        nodes[text(row, 'route_id')].append(row)
    for rows in nodes.values():
        rows.sort(key=lambda r: (number(r['order_num']), str(r['record_id'])))
    materials = defaultdict(list)
    for row in data['source:pro_material']:
        materials[(text(row, 'doc_no'), text(row, 'xtransfer_no'), text(row, 'route_id'))].append(row)
    associations = {(text(r, 'route_id'), text(r, 'item_code')) for r in data['source:pro_route_product']}
    processes = {text(r, 'process_id'): r for r in data['source:pro_process']}
    target_codes = {str(r['code']) for r in data['target:workorder']}
    defects = [LegacyDefect(r, mapping('qc_defective', r)) for r in data['source:qc_defective']]
    seen_reports = Counter(text(r, 'record_id') for r in reports)
    candidates = []
    for code, raw_reports in sorted(orders.items(), key=lambda x: x[0] or ''):
        issues = []
        if not code or not code.strip() or len(code) > 64:
            issues.append('INVALID_WORKORDER_CODE')
        if code in target_codes:
            issues.append('TARGET_CODE_CONFLICT')
        identities = sorted({text(r, 'workorder_id') or '' for r in raw_reports})
        products = sorted({text(r, 'item_code') or '' for r in raw_reports})
        units = {text(r, 'unit_of_measure') for r in raw_reports}
        if len(identities) != 1 or not identities[0]:
            issues.append('SOURCE_IDENTITY_CONFLICT')
        if len(products) != 1 or not products[0] or len(units) != 1 or None in units:
            issues.append('PRODUCT_OR_UNIT_CONFLICT')
        grouped, card_routes = defaultdict(list), defaultdict(set)
        for row in raw_reports:
            key = (code, text(row, 'xtransfer_no'), text(row, 'route_id'))
            grouped[key].append(row)
            card_routes[key[1]].add(key[2])
        executions = []
        for key, raw in sorted(grouped.items(), key=lambda x: canonical(x[0])):
            route = nodes.get(key[2], [])
            ei = []
            if not key[1] or not key[1].strip():
                ei.append('MISSING_CARD')
            if len(card_routes[key[1]]) > 1:
                ei.append('CARD_MULTI_ROUTE_REQUIRES_REVIEW')
            if not route or key[2] not in route_rows:
                ei.append('MISSING_ROUTE')
            if len({text(n, 'order_num') for n in route}) != len(route):
                ei.append('DUPLICATE_NODE_SEQUENCE')
            if route_rows.get(key[2], {}).get('forge_joining_flag') in (1, '1'):
                ei.append('FORGE_BROKEN_STEP_BRANCH')
            process_nodes = defaultdict(list)
            for n in route:
                process_nodes[text(n, 'process_id')].append(n)
            converted, totals = [], defaultdict(Decimal)
            for r in raw:
                ri = []
                pid = text(r, 'process_id')
                matches = process_nodes[pid]
                if len(matches) != 1:
                    ri.append('MISSING_OR_AMBIGUOUS_ROUTE_PROCESS')
                if pid not in processes or str(processes[pid]['process_code']) != text(r, 'process_code'):
                    ri.append('PROCESS_ID_CODE_CONFLICT')
                if (key[2], text(r, 'item_code')) not in associations:
                    ri.append('ROUTE_PRODUCT_NOT_ASSOCIATED')
                if seen_reports[text(r, 'record_id')] != 1:
                    ri.append('DUPLICATE_REPORT_IDENTITY')
                if not r.get('create_time') or r.get('user_id') is None:
                    ri.append('MISSING_TIME_OR_OPERATOR')
                if r.get('reporting_work_type') not in ('SELF', 'UNI'):
                    ri.append('UNKNOWN_REPORT_TYPE')
                try:
                    qty = number(r['reporting_work_quantity'])
                    if not target_decimal(qty):
                        ri.append('INVALID_REPORT_QUANTITY')
                    totals[pid] += qty  # UNI rows already hold each operator's allocation.
                except (ValueError, TypeError, InvalidOperation):
                    ri.append('INVALID_REPORT_QUANTITY')
                converted.append(LegacyReport(r, mapping('pro_reporting_work', r),
                                             text(matches[0], 'record_id') if len(matches) == 1 else None, ri))
            first = text(route[0], 'process_id') if route else None
            qty, evidence, input_issues = input_candidate(materials[key], first)
            ei.extend(input_issues)
            if qty and totals and max(totals.values()) > number(qty):
                ei.append('REPORT_EXCEEDS_INPUT_CANDIDATE')
            if first not in totals:
                ei.append('NO_FIRST_NODE_REPORT')
            if route and totals.get(first, Decimal(0)) > 0 and qty and totals[first] > number(qty):
                ei.append('FIRST_REPORT_EXCEEDS_INPUT')
            ei.extend(sorted({i for r in converted for i in r.issues}))
            executions.append(LegacyExecution(key, converted,
                LegacyRouting(key[2], [text(n, 'record_id') for n in route], sorted(totals)),
                qty, evidence, {p: str(q) for p, q in sorted(totals.items())}, sorted(set(ei))))
        quantities = [e.input_qty_candidate for e in executions]
        planned = str(sum((number(q) for q in quantities), Decimal(0))) if all(q is not None for q in quantities) else None
        if planned is not None and not target_decimal(number(planned)):
            planned = None
        times = sorted(r['create_time'] for r in raw_reports if r.get('create_time'))
        if len(times) != len(raw_reports):
            issues.append('MISSING_PLAN_TIME')
        if planned is None:
            issues.append('PLAN_QUANTITY_UNRESOLVED')
        if len({e.key[2] for e in executions}) > 1:
            issues.append('MULTI_ROUTING_WORKORDER_REQUIRES_REVIEW')
        candidates.append(LegacyWorkOrder(code, 'LOCAL', identities, products,
            times[0] if times else None, times[-1] if times else None, planned,
            'SUM_DISTINCT_CARD_INPUT_MIRROR_CANDIDATE_NOT_ERP_PLAN', executions, sorted(set(issues))))
    return candidates, defects


def summary(candidates, defects):
    executions = [e for w in candidates for e in w.executions]
    reports = [r for e in executions for r in e.reports]
    reasons = Counter(i for w in candidates for i in w.issues)
    reasons.update(i for e in executions for i in e.issues)
    return {
        'ruleVersion': RULE_VERSION, 'workorders': len(candidates), 'executions': len(executions),
        'reports': len(reports), 'defects': len(defects),
        'singleRouteWorkorders': sum(len({e.key[2] for e in w.executions}) == 1 for w in candidates),
        'multiRouteWorkorders': sum(len({e.key[2] for e in w.executions}) > 1 for w in candidates),
        'reportCurrentNodeCandidates': sum(r.node_candidate is not None for r in reports),
        'reportStructuralCandidates': sum(not r.issues for r in reports),
        'workordersWithPlanQuantityCandidate': sum(w.planned_qty_candidate is not None for w in candidates),
        'executionsWithInputCandidate': sum(e.input_qty_candidate is not None for e in executions),
        'structurallyCleanWorkorders': sum(not w.issues and all(not e.issues for e in w.executions) for w in candidates),
        'uniRows': sum(r.raw['reporting_work_type'] == 'UNI' for r in reports),
        'uniAllocatedQuantity': str(sum((number(r.raw['reporting_work_quantity']) for r in reports
                                       if r.raw['reporting_work_type'] == 'UNI'), Decimal(0))),
        'defectTypes': dict(Counter(str(d.raw['type']) for d in defects)),
        'defectsEquivalentToBase': 0, 'defectsPreservedPendingReview': len(defects),
        'finalTargetMappings': 0, 'importedFacts': 0, 'batchGo': False, 's1Go': False,
        'globalBlockers': ['NO_HISTORICAL_READ_ONLY_EXECUTION_MODEL', 'NO_DURABLE_SOURCE_MAPPING',
                           'NO_VERIFIED_TARGET_MASTER_AND_OPERATOR_MAPPINGS'],
        'reasonCounts': dict(sorted(reasons.items()))}


def mysql(config, sql, output=None):
    command = ['mysql', '--defaults-extra-file=' + str(config.resolve()), '--connect-timeout=10',
               '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']
    result = subprocess.run(command, input=sql, text=True, stdout=output or subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=300)
    if result.returncode:
        raise RuntimeError('Read-only MySQL capture failed: ' + result.stderr[:1000])
    return result.stdout


def json_select(dataset, expressions, source):
    fields = ','.join("'" + k + "'," + v for k, v in expressions)
    return "SELECT JSON_OBJECT('dataset','" + dataset + "','row',JSON_OBJECT(" + fields + ')) FROM ' + source + ';'


def capture(config, directory, include_source=True, expected_flyway="39"):
    # Reject the wrong instance before reading any business table. A filename is
    # not an environment identity (mysql-dev.cnf can point somewhere else).
    probe = mysql(config, "SET SESSION TRANSACTION READ ONLY; SELECT JSON_OBJECT('uuid',@@server_uuid,'port',@@port);")
    identity = json.loads(probe.strip())
    if identity != {'uuid': SERVER, 'port': 3306}:
        raise ValueError('Unexpected server: no business tables read')
    metadata_sql = """SET SESSION TRANSACTION READ ONLY;
SELECT JSON_OBJECT('schema',TABLE_SCHEMA,'table',TABLE_NAME,'column',COLUMN_NAME,
'ordinal',ORDINAL_POSITION,'key',COLUMN_KEY,'type',COLUMN_TYPE,'engine',
(SELECT ENGINE FROM information_schema.tables t WHERE t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME))
FROM information_schema.columns c WHERE TABLE_SCHEMA IN ('prod_j2eedb','mes_base_dev_perf_test') ORDER BY TABLE_SCHEMA,TABLE_NAME,ORDINAL_POSITION;"""
    metadata = [json.loads(line) for line in mysql(config, metadata_sql).splitlines()]
    write_json(directory / 'metadata.json', metadata)
    tables = defaultdict(list)
    for row in metadata:
        tables[(row['schema'], row['table'])].append(row)
    statements = ['SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;',
                  'SET SESSION TRANSACTION READ ONLY;', 'START TRANSACTION WITH CONSISTENT SNAPSHOT;']
    statements.append(json_select('server', [('uuid', '@@server_uuid'), ('host', '@@hostname'),
        ('port', '@@port'), ('version', '@@version'), ('timeZone', '@@session.time_zone'),
        ('systemTimeZone', '@@system_time_zone'), ('capturedAt', 'NOW(6)')], '(SELECT 1) s'))
    statements.append(json_select('binding', [(k, 'f.'+k) for k in
        ('factory_id', 'factory_code', 'runtime_mode', 'status', 'datasource_id')] +
        [(k, 'd.'+k) for k in ('db_host', 'db_port', 'db_name')] + [('datasource_status', 'd.status')],
        "mes_base_dev.mf_factory f JOIN mes_base_dev.mf_datasource d USING(datasource_id) WHERE d.db_name='mes_base_dev_perf_test'"))
    statements.append(json_select('flyway', [('version','version'), ('success','success'),
        ('checksum','checksum')], TARGET+'.flyway_business_schema_history ORDER BY installed_rank'))
    statements.append(json_select('activity', [('id','ID'), ('db','DB'), ('command','COMMAND'),
        ('time','TIME'), ('state','STATE')], "information_schema.processlist WHERE COMMAND<>'Sleep' AND ID<>CONNECTION_ID()"))
    # All inspected rows are read in this one transaction. No schema or source is chosen by a fallback.
    source_tables = list(SOURCE_TABLES) if include_source else []
    for table in source_tables:
        columns = tables[(SOURCE, table)]
        if not columns or any(c['engine'] != 'InnoDB' for c in columns):
            raise ValueError('Source table absent or nontransactional: ' + table)
        statements.append(json_select('source:'+table, [(c['column'], 'CAST('+identifier(c['column'])+' AS CHAR)')
            for c in columns], identifier(SOURCE)+'.'+identifier(table)))
    if include_source:
        statements.append(json_select('source:sys_user', [(k, 'CAST('+identifier(k)+' AS CHAR)')
            for k in ('user_id', 'user_name', 'nick_name', 'status', 'del_flag', 'create_time')], SOURCE+'.sys_user'))
        statements.append(json_select('target:sys_user', [(k, 'CAST('+identifier(k)+' AS CHAR)')
            for k in ('user_id', 'user_name', 'nick_name', 'status', 'del_flag', 'create_time')], 'mes_base_dev.sys_user'))
    for (schema, table), columns in sorted(tables.items()):
        if schema != TARGET:
            continue
        if any(c['engine'] != 'InnoDB' for c in columns):
            raise ValueError('Target table nontransactional: ' + table)
        pk = [c['column'] for c in columns if c['key'] == 'PRI']
        if not pk:
            raise ValueError('Cannot protect target table without primary key: ' + table)
        # JSON array has positional, null and type boundaries; SHA covers every existing field.
        array = 'JSON_ARRAY('+','.join(identifier(c['column']) for c in columns)+')'
        statements.append(json_select('baseline:'+table, [('pk','JSON_ARRAY('+','.join(identifier(k) for k in pk)+')'),
            ('hash','SHA2(CAST('+array+' AS CHAR CHARACTER SET utf8mb4),256)')], identifier(TARGET)+'.'+identifier(table)))
        if include_source and table in ('workorder', 'material', 'operation', 'routing', 'routing_node', 'unit_measure'):
            statements.append(json_select('target:'+table, [(c['column'], 'CAST('+identifier(c['column'])+' AS CHAR)')
                for c in columns], identifier(TARGET)+'.'+identifier(table)))
    statements.extend([json_select('end', [('capturedAt', 'NOW(6)')], '(SELECT 1) s'), 'COMMIT;'])
    sql = '\n'.join(statements)
    (directory / 'capture.sql').write_text(sql)
    with (directory / 'snapshot.jsonl').open('x') as out:
        mysql(config, sql, out)
    data = defaultdict(list)
    with (directory / 'snapshot.jsonl').open() as captured:
        for line in captured:
            item = json.loads(line)
            data[item['dataset']].append(item['row'])
    # Empty tables have no result rows, but are still part of the protection set.
    for schema, table in tables:
        if schema == TARGET:
            data.setdefault('baseline:' + table, [])
    for table in source_tables:
        data.setdefault('source:' + table, [])
    data['targetMetadata'] = [r for r in metadata if r['schema'] == TARGET]
    server, = data['server']
    binding, = data['binding']
    if server['uuid'] != SERVER or server['port'] != 3306:
        raise ValueError('Unexpected server: capture rejected')
    expected = {'factory_id': FACTORY, 'factory_code': FACTORY, 'runtime_mode': 'LOCAL', 'status': 'ENABLED',
                'datasource_id': '9', 'db_host': '192.168.18.25', 'db_port': 3306,
                'db_name': TARGET, 'datasource_status': 'ENABLED'}
    if {k: str(v) for k,v in binding.items()} != {k: str(v) for k,v in expected.items()}:
        raise ValueError('Unexpected Factory binding: capture rejected')
    if not data['flyway'] or str(data['flyway'][-1]['version']) != expected_flyway or any(r['success'] != 1 for r in data['flyway']):
        raise ValueError('Unexpected Flyway version: capture rejected')
    return data


def protection(before, after):
    changes, added, removed = {}, {}, {}
    for key in sorted(k for k in before if k.startswith('baseline:')):
        old = {canonical(r['pk']): r['hash'] for r in before[key]}
        new = {canonical(r['pk']): r['hash'] for r in after[key]}
        changes[key] = sum(new.get(k) != h for k,h in old.items() if k in new)
        removed[key] = len(old.keys() - new.keys())
        added[key] = len(new.keys() - old.keys())
    schema_unchanged = before.get('targetMetadata') == after.get('targetMetadata')
    return {'changed': changes, 'removed': removed, 'added': added,
            'targetSchemaUnchanged': schema_unchanged,
            'allExistingRowsUnchanged': schema_unchanged and not any(changes.values()) and not any(removed.values()),
            'noConcurrentAppendsObserved': not any(added.values())}


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql-config', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--batch-id', required=True)
    parser.add_argument('--expected-flyway', choices=('39','40','41','42'), default='39')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False, mode=0o700)
    before_dir, after_dir = args.output/'before', args.output/'after'
    before_dir.mkdir(); after_dir.mkdir()
    before = capture(args.mysql_config, before_dir, expected_flyway=args.expected_flyway)
    candidates, defects = convert(before)
    result = summary(candidates, defects)
    h = hashlib.sha256()
    with (before_dir/'snapshot.jsonl').open('rb') as captured:
        for chunk in iter(lambda: captured.read(1024*1024), b''):
            h.update(chunk)
    batch = LegacyMigrationBatch(args.batch_id, RULE_VERSION, h.hexdigest())
    with (args.output/'workorders.jsonl').open('x') as out:
        for w in candidates:
            out.write(canonical(asdict(w))+'\n')
    with (args.output/'defects.jsonl').open('x') as out:
        for d in defects:
            out.write(canonical(asdict(d))+'\n')
    from conversion_v2 import convert_v2
    ordinary, forge, links, v2 = convert_v2(before, candidates, defects)
    for name, rows in [('ordinary-v2', ordinary), ('forge-excluded-v2', forge), ('forge-links-v2', links)]:
        with (args.output/(name+'.jsonl')).open('x') as stream:
            for row in rows:
                stream.write(canonical(row)+'\n')
    write_json(args.output/'summary-v2.json', v2)
    write_json(args.output/'summary.json', result)
    write_json(args.output/'batch.json', asdict(batch))
    write_json(args.output/'head.json', {'head': subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip(),
        'capturedAt': datetime.now(timezone.utc).isoformat(), 'toolSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()})
    # Same frozen input must produce exactly the same mappings and candidates.
    repeated, repeated_defects = convert(before)
    repeat_ok = len(candidates) == len(repeated) and len(defects) == len(repeated_defects) and \
        all(digest(asdict(a)) == digest(asdict(b)) for a, b in zip(candidates, repeated)) and \
        all(digest(asdict(a)) == digest(asdict(b)) for a, b in zip(defects, repeated_defects))
    write_json(args.output/'repeat.json', {'deterministicConversion': repeat_ok,
        'databaseImportIdempotencyTested': False, 'targetInserts': 0})
    after = capture(args.mysql_config, after_dir, include_source=False, expected_flyway=args.expected_flyway)
    write_json(args.output/'protection.json', protection(before, after))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not repeat_ok or not protection(before, after)['allExistingRowsUnchanged']:
        raise SystemExit('Repeat or protection check failed')


if __name__ == '__main__':
    main()
