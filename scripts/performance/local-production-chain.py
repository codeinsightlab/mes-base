#!/usr/bin/env python3
"""TEST ONLY: deterministic related LOCAL production facts through real HTTP commands.

Never SQL-seeds business history. Resumable journal retains successful requests and
freezes command UUIDs; an uncertain non-idempotent create requires manual review.
The target must be an explicitly named TEST factory on the expected physical DB.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import sqlite3
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def arguments():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', default='http://127.0.0.1:18081')
    p.add_argument('--factory', required=True)
    p.add_argument('--expected-host', required=True)
    p.add_argument('--expected-db', required=True)
    p.add_argument('--operator-credentials', type=Path, required=True)
    p.add_argument('--stock-credentials', type=Path, required=True)
    p.add_argument('--operator-user-id', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--tag', required=True)
    p.add_argument('--cycles', type=int, default=901)
    p.add_argument('--batches', type=int, default=20)
    p.add_argument('--workers', type=int, default=4)
    p.add_argument('--start', type=int, default=1)
    p.add_argument('--end', type=int)
    return p.parse_args()


class Run:
    def __init__(self, a):
        self.a = a
        if 'TEST' not in a.factory or a.factory == 'PROD' or a.expected_db == 'mes_base_prod_business':
            raise ValueError('Performance writes require an isolated TEST factory/schema')
        if not 1 <= a.workers <= 16 or not 1 <= a.batches <= 100 or not 1 <= a.cycles <= 100000:
            raise ValueError('Explicit bounded workload required')
        a.output.mkdir(parents=True, exist_ok=True)
        a.output.chmod(0o700)
        self.lock = threading.RLock()
        self.stop = threading.Event()
        self.auth_lock = threading.Lock()
        self.tokens = {}
        self.credentials = {'operator': json.loads(a.operator_credentials.read_text()),
                            'stock': json.loads(a.stock_credentials.read_text())}
        self.db = sqlite3.connect(a.output / 'journal.sqlite', check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('PRAGMA synchronous=NORMAL')
        self.db.execute('CREATE TABLE IF NOT EXISTS steps(job INTEGER, step TEXT, fingerprint TEXT, state TEXT, method TEXT, path TEXT, response TEXT, PRIMARY KEY(job,step))')
        self.db.execute('CREATE TABLE IF NOT EXISTS timings(id INTEGER PRIMARY KEY, job INTEGER, step TEXT, method TEXT, status INTEGER, ms REAL, started REAL, worker TEXT)')
        self.db.execute('CREATE TABLE IF NOT EXISTS cycles(job INTEGER PRIMARY KEY, state TEXT, ms REAL, execution_id TEXT, workorder_id TEXT, raw_stock_id TEXT, product_stock_id TEXT, quantity TEXT)')
        spec = {'base': a.base, 'factory': a.factory, 'expectedHost': a.expected_host,
                'expectedDb': a.expected_db, 'tag': a.tag, 'cycles': a.cycles, 'batches': a.batches,
                'operatorUserId': a.operator_user_id, 'kind': 'deterministic-production-simulation',
                'coreFactsPerCycle': 5 * a.batches + 11,
                'coreTables': ['mes_production_report', 'mes_material_issue_input', 'mes_quality_release',
                               'mes_production_receipt', 'mes_warehouse_acceptance', 'mes_execution_completion', 'wm_stock_action']}
        f = a.output / 'workload.json'
        if f.exists() and json.loads(f.read_text()) != spec:
            raise ValueError('Resume requires exactly the same business workload')
        f.write_text(json.dumps(spec, indent=2))

    def token(self, actor, force=False):
        with self.auth_lock:
            previous = self.tokens.get(actor)
            if previous and not force and time.monotonic() - previous[1] < 240:
                return previous[0]
            req = urllib.request.Request(self.a.base + '/api/session',
                    data=json.dumps(self.credentials[actor]).encode(),
                    headers={'Content-Type': 'application/json'}, method='POST')
            with urllib.request.urlopen(req, timeout=30) as r:
                session = json.load(r)
            token = session['accessToken']
            self.tokens[actor] = (token, time.monotonic())
            return token

    def request(self, job, step, method, path, body=None, actor='operator'):
        for auth_attempt in range(2):
            token = self.token(actor, force=auth_attempt > 0)
            req = urllib.request.Request(self.a.base + path,
                    data=None if body is None else json.dumps(body).encode(),
                    headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token,
                             'X-Factory-Id': self.a.factory}, method=method)
            start = time.perf_counter()
            started = time.time()
            try:
                with urllib.request.urlopen(req, timeout=45) as r:
                    status, raw = r.status, r.read().decode()
            except urllib.error.HTTPError as e:
                status, raw = e.code, e.read().decode()
            except Exception:
                with self.lock:
                    self.db.execute('INSERT INTO timings(job,step,method,status,ms,started,worker) VALUES(?,?,?,?,?,?,?)',
                        (job, step, method, 0, (time.perf_counter()-start)*1000, started, threading.current_thread().name))
                    self.db.commit()
                raise
            elapsed = (time.perf_counter() - start) * 1000
            result = json.loads(raw) if raw else None
            with self.lock:
                self.db.execute('INSERT INTO timings(job,step,method,status,ms,started,worker) VALUES(?,?,?,?,?,?,?)',
                    (job, step, method, status, elapsed, started, threading.current_thread().name))
                self.db.commit()
            if status == 401 and auth_attempt == 0:
                continue  # Request was rejected before business admission; refresh the same identity.
            if status != 200 or isinstance(result, dict) and isinstance(result.get('code'), int) and result['code'] != 200:
                raise RuntimeError(json.dumps({'job': job, 'step': step, 'path': path, 'status': status,
                                               'response': result}, ensure_ascii=False))
            return result
        raise RuntimeError('Session renewal failed')

    def step(self, job, name, method, path, body=None, actor='operator'):
        frozen = hashlib.sha256(json.dumps([method, path, body, actor], sort_keys=True).encode()).hexdigest()
        with self.lock:
            previous = self.db.execute('SELECT fingerprint,state,response FROM steps WHERE job=? AND step=?', (job, name)).fetchone()
            if previous:
                if previous[0] != frozen:
                    raise RuntimeError('Frozen request changed: ' + str((job, name)))
                if previous[1] == 'SUCCEEDED':
                    return json.loads(previous[2])
                raise RuntimeError('Uncertain request requires review before resuming: ' + str((job, name)))
            self.db.execute('INSERT INTO steps VALUES(?,?,?,?,?,?,?)', (job, name, frozen, 'PENDING', method, path, None))
            self.db.commit()
        response = self.request(job, name, method, path, body, actor)
        with self.lock:
            self.db.execute("UPDATE steps SET state='SUCCEEDED',response=? WHERE job=? AND step=?", (json.dumps(response), job, name))
            self.db.commit()
        return response

    def completed_step(self, job, name):
        with self.lock:
            row = self.db.execute('SELECT state,response FROM steps WHERE job=? AND step=?', (job, name)).fetchone()
        if row is None:
            return None
        if row[0] != 'SUCCEEDED':
            raise RuntimeError('Uncertain request requires review before resuming: ' + str((job, name)))
        return json.loads(row[1])

    def post(self, job, name, path, body, actor='operator', command=False):
        if command:
            body = {'requestId': str(uuid.uuid5(uuid.NAMESPACE_URL, self.a.tag + '/' + str(job) + '/' + name)), **body}
        return self.step(job, name, 'POST', path, body, actor)

    def prepare(self):
        f = self.request(0, 'preflight-factory', 'GET', '/api/system/factory/' + self.a.factory)
        ds = self.request(0, 'preflight-datasource', 'GET', '/api/system/datasource/' + str(f['datasourceId']))
        assert f['status'] == 'ENABLED' and f['runtimeMode'] == 'LOCAL' and f['erpProvider'] is None
        assert ds['status'] == 'ENABLED' and ds['dbHost'] == self.a.expected_host and ds['dbName'] == self.a.expected_db and ds['dbPort'] == 3306
        info = self.request(0, 'preflight-operator', 'GET', '/api/session/info')
        assert str(info['user']['userId']) == self.a.operator_user_id
        (self.a.output / 'binding.json').write_text(json.dumps({'factory': f, 'datasource': ds}, ensure_ascii=False, indent=2))
        tag = self.a.tag
        u = self.post(0, 'unit', '/api/basic/unit-measure', {'code': tag+'_U', 'name': '性能验收基本单位', 'symbol': '件', 'status': 'ENABLED'})
        self.unit = u['id']
        self.raw = self.post(0, 'raw', '/api/basic/material', {'code': tag+'_RAW', 'name': '性能验收通用毛坯', 'unitId': self.unit, 'materialType': 'ITEM', 'status': 'ENABLED'})
        self.warehouses = [self.post(0, 'warehouse'+k, '/api/wm/wm-warehouse', {'code': tag+'_'+k, 'name': '性能验收'+k, 'status': 'ENABLED', 'remark': tag}) for k in ['RAW', 'FG']]
        assert all(w['source'] == 'LOCAL' for w in self.warehouses)
        self.operations = []
        for n in 'ABCDE':
            op = self.post(0, 'operation'+n, '/api/production/operation', {'code': tag+'_'+n, 'name': {'A':'下料','B':'粗加工','C':'精加工','D':'装配','E':'终检'}[n], 'status': 'ENABLED', 'sort': ord(n)-65, 'description': tag})
            self.operations.append(op)
            self.post(0, 'capability'+n, '/api/production/mes-operation-user', {'operationId': op['id'], 'userId': self.a.operator_user_id, 'remark': tag})
        self.products, self.routes, self.nodes = [], [], []
        for i in range(3):
            p = self.post(0, 'product'+str(i), '/api/basic/material', {'code':tag+'_P'+str(i), 'name':'性能验收产品系列'+str(i+1), 'unitId':self.unit, 'materialType':'PRODUCT', 'status':'ENABLED'})
            r = self.post(0, 'routing'+str(i), '/api/production/routing', {'code':tag+'_R'+str(i), 'name':'性能验收五工序系列'+str(i+1), 'description':tag})
            graph = self.step(0, 'graph'+str(i), 'PUT', '/api/production/routing/'+r['id']+'/content', {'revision':r['revision'], 'materialIds':[p['id']], 'nodes':[{'nodeCode':n,'operationId':op['id'],'displayOrder':j,'isKey':n=='E','description':tag} for j,(n,op) in enumerate(zip('ABCDE', self.operations))], 'edges':[{'fromNodeCode':a,'toNodeCode':b} for a,b in zip('ABCD','BCDE')]})
            self.post(0, 'publish'+str(i), '/api/production/routing/'+r['id']+'/actions/publish', {'revision':graph['routing']['revision']})
            read = self.request(0, 'nodes'+str(i), 'GET', '/api/production/routing/'+r['id'])
            self.products.append(p);self.routes.append(r);self.nodes.append({n['nodeCode']:n['id'] for n in read['nodes']})

    def cycle(self, job):
        if (self.a.output / 'STOP').exists():
            self.stop.set()
        if self.stop.is_set():
            return
        begin = time.perf_counter()
        variant = (job-1) % 3
        plan = Decimal([80, 100, 120][variant])
        p, r, nodes = self.products[variant], self.routes[variant], self.nodes[variant]
        tag = self.a.tag + '_' + str(job).zfill(6)
        try:
            stocks = [self.post(job, 'stock'+k, '/api/wm/wm-inventory', {'materialId':material['id'], 'warehouseId':w['id'], 'quantity':'0', 'availableQuantity':'0', 'status':'ENABLED', 'batchNo':tag+'_'+k, 'binCode':k, 'remark':tag}) for k,material,w in zip(['RAW','FG'],[self.raw,p],self.warehouses)]
            raw, fg = stocks
            doc = self.post(job,'opening','/api/wm/stock-documents', {'documentType':'OTHER_RECEIPT','reason':tag+' 验收原料入库'}, 'stock')
            did = doc['document']['id']
            self.post(job,'opening-line','/api/wm/stock-documents/'+did+'/lines', {'expectedRevision':'0','stockId':raw['id'],'materialId':self.raw['id'],'warehouseId':self.warehouses[0]['id'],'unitId':self.unit,'batchNo':raw['batchNo'],'binCode':'RAW','quantity':str(plan*2),'remark':tag}, 'stock')
            self.post(job,'opening-post','/api/wm/stock-documents/'+did+'/actions/approve', {'expectedRevision':'1'}, 'stock', True)
            order = self.post(job,'workorder','/api/production/workorder', {'code':tag+'_WO','productId':p['id'],'plannedQty':str(plan),'routingId':r['id'],'remark':tag+' 规则驱动仿真，非现场真实记录'})
            wid = order['workorder']['id']
            self.step(job,'material','PUT','/api/production/workorder/'+wid+'/material', {'expectedRevision':0,'materialId':self.raw['id'],'totalRequiredQty':str(plan*2),'warehouseIds':[self.warehouses[0]['id']],'workCenter':'PERF_TEST','enabled':True})
            self.post(job,'release-order','/api/production/workorder/'+wid+'/actions/release', {'revision':order['workorder']['revision']})
            inp = self.completed_step(job, 'input')
            if inp is None:
                code = self.raw['code']+'#'+raw['batchNo']
                query = urllib.parse.urlencode({'code':code,'workorderId':wid,'stockReference':raw['id'],'outputQty':str(plan)})
                calc = self.request(job,'calculate','GET','/api/production/input/calculate?'+query)
                assert Decimal(calc['issueQty']) == plan*2
                inp = self.post(job,'input','/api/production/input', {'code':code,'workorderId':wid,'stockReference':raw['id'],'outputQty':str(plan),'routingId':r['id'],'routingNodeIds':[nodes[n] for n in 'ABCDE'],'inputKind':'PRODUCTION_INPUT','calculationKey':calc['calculationKey']}, command=True)
            eid = inp['executionId']
            for n in 'ABCDE':
                self.post(job,'assignment'+n,'/api/production/mes-execution-assignment', {'executionId':eid,'routingNodeId':nodes[n],'userId':self.a.operator_user_id,'remark':tag})
            for n in 'ABCDE':
                for part in range(self.a.batches):
                    self.post(job,'report'+n+str(part),'/api/production/reporting', {'executionId':eid,'routingNodeId':nodes[n],'reportQty':str(plan/self.a.batches)}, command=True)
            self.post(job,'quality-release','/api/quality/releases/executions/'+eid, {'expectedReleaseId':None,'quantity':str(plan),'reason':tag+' 完整工序数量核对，放行'}, command=True)
            for split, fraction in enumerate([Decimal('0.6'),Decimal('0.4')]):
                q = str(plan*fraction)
                receipt = self.post(job,'handover'+str(split),'/api/production/receipts', {'executionId':eid,'warehouseId':self.warehouses[1]['id'],'productLotCode':fg['batchNo'],'quantity':q}, command=True)
                self.post(job,'accept'+str(split),'/api/wm/production-acceptances/receipts/'+receipt['id'], {'stockId':fg['id'],'quantity':q,'reason':tag+' 分批实收'}, command=True)
            closure = self.request(job,'closure','GET','/api/production/executions/'+eid+'/closure')
            self.post(job,'complete','/api/production/executions/'+eid+'/actions/complete', {'expectedBasisHash':closure['basisHash'],'reason':tag+' 产出、放行、交接及接收一致'}, command=True)
            with self.lock:
                self.db.execute('INSERT OR REPLACE INTO cycles VALUES(?,?,?,?,?,?,?,?)', (job,'COMPLETED',(time.perf_counter()-begin)*1000,eid,wid,raw['id'],fg['id'],str(plan)))
                self.db.commit()
        except Exception as failure:
            self.stop.set()
            with self.lock:
                with (self.a.output/'failures.jsonl').open('a') as f:
                    f.write(json.dumps({'job':job,'error':str(failure),'time':time.time()},ensure_ascii=False)+'\n')
            raise

    def progress(self):
        with self.lock:
            completed = self.db.execute("SELECT COUNT(*) FROM cycles WHERE state='COMPLETED'").fetchone()[0]
            stats = self.db.execute('SELECT COUNT(*),COALESCE(SUM(status<>200),0) FROM timings').fetchone()
        state = {'time':time.time(),'completedCycles':completed,'minimumCoreFactsInCompletedCycles':completed*(5*self.a.batches+11),'requests':stats[0],'non200':stats[1],'stoppedOnFailure':self.stop.is_set()}
        (self.a.output/'progress.json').write_text(json.dumps(state,indent=2))
        print(json.dumps(state),flush=True)

    def execute(self):
        self.prepare()
        last = self.a.end or self.a.cycles
        jobs = range(self.a.start, min(last,self.a.cycles)+1)
        with self.lock:
            done = {r[0] for r in self.db.execute("SELECT job FROM cycles WHERE state='COMPLETED'")}
        with ThreadPoolExecutor(max_workers=self.a.workers) as pool:
            futures = [pool.submit(self.cycle, j) for j in jobs if j not in done]
            outstanding = set(futures)
            while outstanding:
                import concurrent.futures
                _, outstanding = concurrent.futures.wait(outstanding,timeout=30,return_when=concurrent.futures.ALL_COMPLETED)
                self.progress()
            for f in futures:
                f.result()
        self.progress()


if __name__ == '__main__':
    Run(arguments()).execute()
