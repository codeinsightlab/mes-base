#!/usr/bin/env python3
"""Verify a migrated sample with formal HTTP reads and denied production commands."""
import argparse
from collections import Counter
from decimal import Decimal
import json
import os
from pathlib import Path
import urllib.request
import urllib.error
import urllib.parse
import uuid


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base',required=True);p.add_argument('--credentials',type=Path,required=True)
    p.add_argument('--command',type=Path,required=True);p.add_argument('--workorder-id',required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--allow-existing-cards',action='store_true')
    a=p.parse_args();a.output.mkdir(mode=0o700,parents=True)
    command=json.loads(a.command.read_text());token=None;checks=[];counter=0
    def request(name,path,method='GET',body=None,expected=200,factory=command['factoryId'],auth=True):
        nonlocal counter
        headers={'Content-Type':'application/json'}
        if factory:headers['X-Factory-Id']=factory
        if auth and token:headers['Authorization']='Bearer '+token
        req=urllib.request.Request(a.base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,timeout=30) as response:status=response.status;raw=response.read()
        except urllib.error.HTTPError as error:status=error.code;raw=error.read()
        result=json.loads(raw) if raw else None
        counter+=1
        # Access tokens never enter evidence files.
        if name!='login':(a.output/(str(counter)+'-'+name+'.json')).write_text(json.dumps({'path':path,'method':method,'status':status,'body':result},ensure_ascii=False,indent=2))
        checks.append({'name':name,'status':status,'expected':expected})
        if status!=expected:raise AssertionError(name+' expected '+str(expected)+' got '+str(status))
        return result
    token=request('login','/api/session','POST',json.loads(a.credentials.read_text()))['accessToken']
    wid=a.workorder_id
    detail=request('order-detail','/api/production/workorder/'+wid);order=detail['workorder']
    assert order['status']=='HISTORICAL' and order['source']=='LOCAL' and Decimal(str(order['plannedQty']))==Decimal(command['plannedQty'])
    listing=request('order-list','/api/production/workorder?code='+urllib.parse.quote(command['code']))
    assert listing['total']==1 and not listing['items'][0]['erpExtraMissing']
    summary=request('execution-summary','/api/production/report-quantities/workorders/'+wid+'?limit=100')
    assert summary['total']>=len(command['executions']) if a.allow_existing_cards else summary['total']==len(command['executions'])
    if len(json.loads(command['sourcePayload'])['executions'])==len(command['executions']):
        assert summary['total']==len(command['executions'])
    eids=[];report_ids=[]
    for ix,entry in enumerate(summary['items']):
        if a.allow_existing_cards:
            probe=request('history-probe-'+str(ix),'/api/production/executions/'+str(entry['executionId'])+'/legacy-history')
            source_key=json.loads(probe['sourceEvidence'])['key']
            if not any(json.loads(e['sourceKey'])==source_key for e in command['executions']):continue
        eid=entry['executionId'];eids.append(eid)
        execution=request('execution-'+str(ix),'/api/production/mes-production-execution/'+eid)
        assert execution['status']=='HISTORICAL' and execution['startTime'] is None and execution['endTime'] is None
        path=request('path-'+str(ix),'/api/production/executions/'+eid+'/path')
        evidence=request('history-'+str(ix),'/api/production/executions/'+eid+'/legacy-history')
        assert evidence['historical'] and evidence['readOnly']
        if not a.allow_existing_cards:assert evidence['batchId']==command['batchId']
        source=json.loads(evidence['sourceEvidence']);legacy_key=source['key']
        approved=next(e for e in command['executions'] if json.loads(e['sourceKey'])==legacy_key)
        quantity=request('quantity-'+str(ix),'/api/production/report-quantities/executions/'+eid)
        assert quantity['historical'] and not quantity['writable'] and Decimal(quantity['confirmedInputQty'])==0
        assert Decimal(quantity['productionInputQty'])==Decimal(approved['inputQty'])==Decimal(entry['productionInputQty'])
        assert len(quantity['nodes'])==len(approved['nodes'])==len(path['selectedNodeIds'])
        for n,original in zip(quantity['nodes'],approved['nodes']):
            assert Decimal(n['reportedQty'])==Decimal(original['legacyNet'])+Decimal(original['grossCorrection'])
            assert Decimal(n['effectiveOutputQty'])==Decimal(original['legacyNet']) and Decimal(n['availableQty'])==0
            assert Decimal(n['defectQty'])==Decimal(original['effectiveDefect']) and not n['historicalConflict']
            ledger_path='/api/production/reports?executionId='+eid+'&routingNodeId='+n['routingNodeId']+'&limit=100'
            ledger=request('ledger-'+str(ix)+'-'+str(n['sequenceNo']),ledger_path)
            raw_reports=[r for r in approved['reports'] if r['processId']==original['processId']]
            assert ledger['total']==len(raw_reports)
            rows=list(ledger['items'])
            while len(rows)<ledger['total']:
                page=request('ledger-page-'+str(ix)+'-'+str(n['sequenceNo'])+'-'+str(len(rows)),ledger_path+'&offset='+str(len(rows)))
                assert page['items'] and page['total']==ledger['total']
                rows.extend(page['items'])
            assert len(rows)==len(raw_reports)
            expected=Counter((str(r['operatorUserId']),Decimal(r['quantity']),r['reportTime']) for r in raw_reports)
            actual=Counter((str(row['operatorUserId']),Decimal(row['quantityDelta']),row['reportTime']) for row in rows)
            assert expected==actual,'Original operator/time/net quantity multiplicity did not survive'
            assert len({row['id'] for row in rows})==len(rows)
            for row in rows:
                assert row['recordedBy']=='100';report_ids.append(row['id'])
        if len(eids)==1:
            request('deny-start','/api/production/executions/'+eid+'/actions/start','POST',expected=409)
            request('deny-path','/api/production/executions/'+eid+'/actions/select-path','POST',{'revision':0,'routingNodeIds':path['selectedNodeIds']},expected=409)
            request('deny-execution-edit','/api/production/mes-production-execution/'+eid,'PUT',{'workorderId':wid,'routingId':execution['routingId'],'targetQty':'1','status':'READY'},expected=409)
            node=quantity['nodes'][0]['routingNodeId']
            request('deny-report','/api/production/reporting','POST',{'executionId':eid,'routingNodeId':node,'reportQty':'1','requestId':str(uuid.uuid4())},expected=409)
            request('deny-supplement','/api/production/reports/supplements','POST',{'executionId':eid,'routingNodeId':node,'quantity':'1','operatorUserId':approved['reports'][0]['operatorUserId'],'reportTime':'2026-01-01T10:00:00','reason':'TEST ONLY history refusal','requestId':str(uuid.uuid4())},expected=409)
            request('deny-adjust','/api/production/reports/'+report_ids[0]+'/adjustments','POST',{'quantityDelta':'1','reason':'TEST ONLY history refusal','requestId':str(uuid.uuid4())},expected=409)
            request('deny-void','/api/production/reports/'+report_ids[0]+'/void','POST',{'reason':'TEST ONLY history refusal','requestId':str(uuid.uuid4())},expected=409)
            request('deny-complete','/api/production/executions/'+eid+'/actions/complete','POST',{'requestId':str(uuid.uuid4()),'expectedBasisHash':'TEST_ONLY','reason':'TEST ONLY history refusal'},expected=409)
            request('deny-quality-release','/api/quality/releases/executions/'+eid,'POST',{'requestId':str(uuid.uuid4()),'quantity':'1','reason':'TEST ONLY history refusal'},expected=409)
            request('deny-receipt','/api/production/receipts','POST',{'executionId':eid,'warehouseId':'1','quantity':'1','requestId':str(uuid.uuid4())},expected=409)
            request('deny-issue','/api/issue/mes-material-issue','POST',{'issueNo':'TEST_HISTORY_DENY_'+str(uuid.uuid4()),'workorderId':wid,'executionId':eid,'productId':execution['productId'],'routingId':execution['routingId'],'status':'DRAFT'},expected=409)
            request('deny-defect','/api/quality/defects','POST',{'requestKey':str(uuid.uuid4()),'facts':{'executionId':eid,'affectedNodeId':node,'discoveredNodeId':node,'defectQty':'1','wasteCategory':'MATERIAL','defectReason':'TEST ONLY history refusal','destinationType':'WAREHOUSE','targetWarehouseId':'1','occurredAt':'2026-01-01T10:00:00'}},expected=409)
            request('deny-anonymous','/api/production/executions/'+eid+'/legacy-history',expected=401,auth=False)
            request('deny-wrong-factory','/api/production/executions/'+eid+'/legacy-history',expected=422,factory='NO_SUCH_LEGACY_FACTORY')
    assert len(eids)==len(command['executions'])
    request('deny-release','/api/production/workorder/'+wid+'/actions/release','POST',{'revision':0},expected=409)
    request('deny-order-edit','/api/production/workorder/'+wid,'PUT',{'code':order['code'],'productId':order['productId'],'plannedQty':str(order['plannedQty']),'routingId':order['routingId'],'revision':0},expected=409)
    request('deny-cancel','/api/production/workorder/'+wid+'/actions/cancel','POST',{'requestId':str(uuid.uuid4()),'expectedRevision':0,'reason':'TEST ONLY history refusal'},expected=409)
    result={'passed':True,'workorderId':wid,'executions':eids,'reportsVerified':len(report_ids),'checks':checks}
    (a.output/'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({'passed':True,'executions':len(eids),'reportsVerified':len(report_ids),'httpChecks':len(checks)}))


if __name__=='__main__':main()
