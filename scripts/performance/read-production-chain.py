#!/usr/bin/env python3
"""TEST ONLY read latency at the explicitly recorded production-simulation scale."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import importlib.util
import json
from pathlib import Path
import sqlite3
import threading
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--credentials', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--samples', type=int, default=32)
    parser.add_argument('--label', required=True)
    a = parser.parse_args()
    if not 1 <= a.workers <= 16 or not 8 <= a.samples <= 1000:
        raise ValueError('Bounded sample size and concurrency required')
    w = json.loads((a.directory/'workload.json').read_text())
    if w['factory'] == 'PROD' or 'TEST' not in w['factory']:
        raise ValueError('Isolated TEST factory required')
    credentials = json.loads(a.credentials.read_text())
    req = urllib.request.Request(w['base']+'/api/session',data=json.dumps(credentials).encode(),headers={'Content-Type':'application/json'},method='POST')
    with urllib.request.urlopen(req,timeout=30) as r:
        token = json.load(r)['accessToken']
    c = sqlite3.connect(a.directory/'journal.sqlite')
    cycle = c.execute("SELECT execution_id,workorder_id FROM cycles WHERE state='COMPLETED' ORDER BY job DESC LIMIT 1").fetchone()
    eid, wid = cycle
    quantity_url = '/api/production/report-quantities/executions/'+eid
    def fetch(path):
        req = urllib.request.Request(w['base']+path,headers={'Authorization':'Bearer '+token,'X-Factory-Id':w['factory']})
        start = time.perf_counter()
        with urllib.request.urlopen(req,timeout=30) as r:
            status,raw = r.status,r.read()
        return status,(time.perf_counter()-start)*1000,json.loads(raw)
    _,_,q = fetch(quantity_url)
    node = q['nodes'][0]['routingNodeId']
    total_w = fetch('/api/production/workorder?offset=0&limit=20')[2]['total']
    total_s = fetch('/api/wm/wm-inventory?offset=0&limit=20')[2]['total']
    endpoints = {
        'workorder-first':'/api/production/workorder?offset=0&limit=20',
        'workorder-last':'/api/production/workorder?offset='+str(max(0,int(total_w)-20))+'&limit=20',
        'inventory-first':'/api/wm/wm-inventory?offset=0&limit=20',
        'inventory-last':'/api/wm/wm-inventory?offset='+str(max(0,int(total_s)-20))+'&limit=20',
        'execution-first':'/api/production/mes-production-execution?offset=0&limit=20',
        'execution-quantity':quantity_url,
        'report-ledger':'/api/production/reports?executionId='+eid+'&routingNodeId='+node+'&offset=0&limit=20',
        'execution-closure':'/api/production/executions/'+eid+'/closure'}
    spec = importlib.util.spec_from_file_location('perf_summary',Path(__file__).with_name('summarize-production-chain.py'))
    module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    result = {'label':a.label,'factory':w['factory'],'workers':a.workers,'samplesPerEndpoint':a.samples,
              'workorderCount':total_w,'inventoryCount':total_s,'executionId':eid,'endpoints':{}}
    samples = []
    for name,path in endpoints.items():
        # Two explicit warmups per endpoint are excluded, with no Redis/token flush.
        fetch(path);fetch(path)
        start = time.perf_counter()
        def one(i):
            begin=time.time()
            try:
                status,ms,value=fetch(path)
                return {'endpoint':name,'sample':i,'started':begin,'status':status,'ms':ms,'total':value.get('total')}
            except Exception as e:
                return {'endpoint':name,'sample':i,'started':begin,'status':0,'error':str(e)}
        with ThreadPoolExecutor(max_workers=a.workers) as pool:
            group=list(pool.map(one,range(a.samples)))
        elapsed=time.perf_counter()-start
        samples.extend(group);values=[r['ms'] for r in group if r['status']==200]
        result['endpoints'][name]={'requests':len(group),'success':len(values),'errors':len(group)-len(values),
           'p50Ms':module.percentile(values,50),'p95Ms':module.percentile(values,95),'p99Ms':module.percentile(values,99),
           'requestsPerSecond':len(group)/elapsed,'path':path}
    f=a.directory/('read-'+a.label+'.json');f.write_text(json.dumps(result,ensure_ascii=False,indent=2))
    with (a.directory/('read-'+a.label+'.jsonl')).open('w') as log:
        for row in samples:log.write(json.dumps(row)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    if any(v['errors'] for v in result['endpoints'].values()):
        raise SystemExit('Read errors; inspect samples')


if __name__ == '__main__':
    main()
