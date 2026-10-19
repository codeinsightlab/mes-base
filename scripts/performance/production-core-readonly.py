#!/usr/bin/env python3
"""Read-only, TEST DEV production review. Never changes server instrumentation or data."""
import argparse
import datetime
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql-config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--samples', type=int, default=20)
    args = parser.parse_args()
    if not 1 <= args.samples <= 120:
        parser.error('samples must be 1..120')
    args.output.mkdir(parents=True, exist_ok=True)
    queries = []

    def query(name, sql, database=None):
        # Only literal diagnostic statements authored below are executed.
        assert sql.lstrip().upper().startswith(('SELECT ', 'SHOW ', 'EXPLAIN ANALYZE SELECT '))
        queries.append({'name': name, 'database': database, 'sql': sql})
        command = ['mysql', '--defaults-extra-file=' + str(args.mysql_config.resolve()),
                   '--connect-timeout=5', '--xml']
        if database:
            command.append(database)
        started = time.monotonic()
        result = subprocess.run(command, input=sql, capture_output=True, text=True, timeout=20)
        if result.returncode:
            return {'error': result.stderr.strip(), 'clientWallMs': (time.monotonic()-started)*1000}
        return {'rows': [{f.get('name'): f.text for f in r} for r in ET.fromstring(result.stdout).findall('row')],
                'clientWallMs': (time.monotonic()-started)*1000}

    identity = query('server', 'SELECT @@hostname AS hostname,@@server_uuid AS uuid,@@version AS version,@@port AS port')
    assert identity['rows'][0]['uuid'] == '03dbc952-7403-11ef-802c-0242ac130002', 'Unexpected MySQL server'
    binding = query('binding', "SELECT f.factory_id,f.factory_code,f.status,f.runtime_mode,f.datasource_id,d.db_host,d.db_port,d.db_name,d.status AS datasource_status FROM mes_base_dev.mf_factory f JOIN mes_base_dev.mf_datasource d USING(datasource_id) WHERE f.factory_code='TEST_DEV_PERF_20261019'")
    b, = binding['rows']
    assert (b['factory_id'], b['datasource_id'], b['db_host'], b['db_port'], b['db_name'], b['status'], b['datasource_status']) == ('TEST_DEV_PERF_20261019', '9', '192.168.18.25', '3306', 'mes_base_dev_perf_test', 'ENABLED', 'ENABLED')
    db = b['db_name']
    output = {'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'server': identity,
              'binding': binding, 'scope': 'new read-only observation; no HTTP load and no business DML'}
    checks = {
        'variables': "SHOW GLOBAL VARIABLES WHERE Variable_name IN ('innodb_flush_log_at_trx_commit','sync_binlog','log_bin','datadir','innodb_flush_method','innodb_io_capacity','innodb_buffer_pool_size','transaction_isolation')",
        'consumers': 'SELECT NAME,ENABLED FROM performance_schema.setup_consumers',
        'fileIO': "SELECT EVENT_NAME,COUNT_STAR,SUM_TIMER_WAIT,MAX_TIMER_WAIT,COUNT_READ,SUM_TIMER_READ,COUNT_WRITE,SUM_TIMER_WRITE,COUNT_MISC,SUM_TIMER_MISC,MAX_TIMER_MISC FROM performance_schema.file_summary_by_event_name WHERE EVENT_NAME LIKE '%binlog%' OR EVENT_NAME LIKE '%innodb_log%'",
        'commits': "SELECT EVENT_NAME,COUNT_STAR,SUM_TIMER_WAIT,MAX_TIMER_WAIT FROM performance_schema.events_statements_summary_global_by_event_name WHERE EVENT_NAME IN ('statement/sql/commit','statement/com/Commit')",
        'status': "SHOW GLOBAL STATUS WHERE Variable_name IN ('Uptime','Innodb_os_log_fsyncs','Innodb_os_log_pending_fsyncs','Innodb_os_log_pending_writes','Innodb_log_waits','Innodb_log_write_requests','Innodb_log_writes','Innodb_data_fsyncs','Innodb_data_pending_fsyncs','Innodb_row_lock_waits','Innodb_row_lock_time','Threads_running','Threads_connected','Binlog_cache_disk_use','Binlog_cache_use')",
        'flyway': "SELECT version,success FROM flyway_business_schema_history ORDER BY installed_rank DESC LIMIT 1",
        'counts': 'SELECT COUNT(*) AS reports,COUNT(DISTINCT execution_id) AS executions FROM mes_production_report',
        'qualityMaximums': 'SELECT (SELECT COALESCE(MAX(n),0) FROM (SELECT execution_id,COUNT(DISTINCT declaration_id) n FROM mes_production_defect GROUP BY execution_id) d) AS max_declarations,(SELECT COALESCE(MAX(n),0) FROM (SELECT execution_id,COUNT(*) n FROM mes_quality_treatment GROUP BY execution_id) t) AS max_events',
        'reportIndexes': 'SHOW INDEX FROM mes_production_report',
    }
    for name, sql in checks.items():
        output[name] = query(name, sql, db if name in ('flyway', 'counts', 'qualityMaximums', 'reportIndexes') else None)
    wait_sql = """SELECT w.REQUESTING_ENGINE_TRANSACTION_ID AS requesting_trx,w.BLOCKING_ENGINE_TRANSACTION_ID AS blocking_trx,
      r.THREAD_ID AS requesting_thread,b.THREAD_ID AS blocking_thread,
      r.OBJECT_SCHEMA,r.OBJECT_NAME,r.INDEX_NAME,r.LOCK_TYPE,r.LOCK_MODE,r.LOCK_STATUS,r.LOCK_DATA,
      b.LOCK_MODE AS blocking_mode,b.LOCK_STATUS AS blocking_status
      FROM performance_schema.data_lock_waits w
      JOIN performance_schema.data_locks r ON r.ENGINE=w.ENGINE AND r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
      JOIN performance_schema.data_locks b ON b.ENGINE=w.ENGINE AND b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID
      WHERE r.OBJECT_SCHEMA='mes_base_dev_perf_test'"""
    output['waitSamples'] = []
    for i in range(args.samples):
        output['waitSamples'].append({'epoch': time.time(), **query('wait-'+str(i), wait_sql)})
        time.sleep(0.2)
    # Same SQL shape and explicit column list as the generated ledger mapper.
    xml = ET.parse('mes-business/src/main/resources/dev/mesbase/business/production/dao/MesProductionReportMapper.xml')
    columns = next(x for x in xml.getroot() if x.tag == 'sql' and x.get('id') == 'Base_Column_List').text.strip()
    node = query('ledger-node', 'SELECT routing_node_id,COUNT(*) AS n FROM mes_production_report WHERE execution_id=901 GROUP BY routing_node_id ORDER BY n DESC,routing_node_id LIMIT 1', db)['rows'][0]
    output['ledgerSample'] = node
    where = ' FROM mes_production_report WHERE execution_id=901 AND routing_node_id=' + str(int(node['routing_node_id']))
    output['plans'] = {}
    for name, sql in {
        'ledgerCount': 'SELECT COUNT(*)' + where,
        'ledgerFirst': 'SELECT ' + columns + where + ' ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET 0',
        'ledgerLast': 'SELECT ' + columns + where + ' ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET ' + str(max(0, int(node['n'])-20)),
        'globalDeepDiagnosticOnly': 'SELECT ' + columns + ' FROM mes_production_report ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET 90000',
        'nodeTotals': 'SELECT p.routing_node_id,COALESCE(SUM(r.quantity_delta),0) FROM mes_execution_path_node p LEFT JOIN mes_production_report r ON r.execution_id=p.execution_id AND r.routing_node_id=p.routing_node_id WHERE p.execution_id=901 GROUP BY p.routing_node_id',
    }.items():
        output['plans'][name] = query(name, 'EXPLAIN ANALYZE ' + sql, db)
    busiest = query('busiest-node', 'SELECT execution_id,routing_node_id,COUNT(*) AS n FROM mes_production_report GROUP BY execution_id,routing_node_id ORDER BY n DESC,execution_id,routing_node_id LIMIT 1', db)['rows'][0]
    output['busiestLedgerSample'] = busiest
    busiest_where = ' FROM mes_production_report WHERE execution_id=' + str(int(busiest['execution_id'])) + ' AND routing_node_id=' + str(int(busiest['routing_node_id']))
    output['plans']['busiestLedgerLast'] = query('busiestLedgerLast', 'EXPLAIN ANALYZE SELECT ' + columns + busiest_where + ' ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET ' + str(max(0, int(busiest['n'])-20)), db)
    for name in ('fileIO', 'commits', 'status'):
        output[name+'After'] = query(name+'After', checks[name])
    (args.output/'mysql-readonly.json').write_text(json.dumps(output, ensure_ascii=False, indent=2)+'\n')
    (args.output/'queries.json').write_text(json.dumps(queries, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'capturedAt': output['capturedAt'], 'samples': len(output['waitSamples']),
                      'nonemptyWaitSamples': sum(bool(x.get('rows')) for x in output['waitSamples']),
                      'errors': [name for name, v in output.items() if isinstance(v, dict) and 'error' in v],
                      'output': str(args.output)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
