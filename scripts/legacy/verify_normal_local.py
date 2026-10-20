#!/usr/bin/env python3
"""TEST ONLY V2.1 real LOCAL cycle; existing authorized opening API, isolated new resources."""
import importlib.util
from pathlib import Path
from decimal import Decimal
import time
import json
import uuid
import urllib.parse
spec=importlib.util.spec_from_file_location('local_chain',Path(__file__).parents[1]/'performance/local-production-chain.py')
chain=importlib.util.module_from_spec(spec);spec.loader.exec_module(chain)


class V21Run(chain.Run):
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
            self.post(job,'opening','/api/wm/local-stock/opening', {'stockId':raw['id'],'quantity':str(plan*2),'expectedQuantity':'0','expectedAvailable':'0','reason':tag+' TEST ONLY 本轮新库存期初'}, 'stock', True)
            order = self.post(job,'workorder','/api/production/workorder', {'code':tag+'_WO','productId':p['id'],'plannedQty':str(plan),'routingId':r['id'],'remark':tag+' 规则驱动仿真，非现场真实记录'})
            wid = order['workorder']['id']
            order = self.step(job,'edit-order','PUT','/api/production/workorder/'+wid, {'code':tag+'_WO','productId':p['id'],'plannedQty':str(plan),'routingId':r['id'],'revision':order['workorder']['revision'],'remark':tag+' TEST ONLY 编辑后完整闭环'})
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
            assert self.request(job,'ready-state','GET','/api/production/mes-production-execution/'+eid)['status']=='READY'
            for n in 'ABCDE':
                self.post(job,'assignment'+n,'/api/production/mes-execution-assignment', {'executionId':eid,'routingNodeId':nodes[n],'userId':self.a.operator_user_id,'remark':tag})
            for n in 'ABCDE':
                for part in range(self.a.batches):
                    report = self.post(job,'report'+n+str(part),'/api/production/reporting', {'executionId':eid,'routingNodeId':nodes[n],'reportQty':str(plan/self.a.batches)}, command=True)
                    if n=='A' and part==0:
                        assert self.request(job,'running-state','GET','/api/production/mes-production-execution/'+eid)['status']=='RUNNING'
                        repeated=self.request(job,'first-report-uuid-replay','POST','/api/production/reporting', {'requestId':str(uuid.uuid5(uuid.NAMESPACE_URL,self.a.tag+'/'+str(job)+'/reportA0')),'executionId':eid,'routingNodeId':nodes[n],'reportQty':str(plan/self.a.batches)})
                        assert repeated==report

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


if __name__=='__main__':
    V21Run(chain.arguments()).execute()
