#!/usr/bin/env python3
"""TEST ONLY: read-only MySQL audit of completed, related production simulation facts."""
import argparse
from decimal import Decimal
import json
import re
from pathlib import Path
import sqlite3
import subprocess
import xml.etree.ElementTree as ET


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('directory',type=Path)
    p.add_argument('--mysql-config',type=Path,required=True)
    p.add_argument('--partial',action='store_true',help='Audit completed cycles only; never declares full acceptance')
    a=p.parse_args();w=json.loads((a.directory/'workload.json').read_text())
    if 'TEST' not in w['factory'] or w['expectedDb']=='mes_base_prod_business':
        raise ValueError('Isolated TEST schema required')
    allowed=['mes_production_report','mes_material_issue_input','mes_quality_release','mes_production_receipt','mes_warehouse_acceptance','mes_execution_completion','wm_stock_action']
    assert w['coreTables']==allowed,'Unexpected audit tables'
    assert re.fullmatch(r'[A-Za-z0-9_]{1,64}',w['expectedDb']) and str(w['operatorUserId']).isdigit(),'Invalid SQL identity'
    c=sqlite3.connect(a.directory/'journal.sqlite');records=c.execute("SELECT job,execution_id,workorder_id,raw_stock_id,product_stock_id,quantity FROM cycles WHERE state='COMPLETED' ORDER BY job").fetchall()
    assert records,'No completed cycles to audit'
    if not a.partial:
        assert len(records)==w['cycles'],'Full planned workload has not completed'
        assert c.execute("SELECT COUNT(*) FROM steps WHERE state<>'SUCCEEDED'").fetchone()[0]==0,'Unresolved steps exist'
    for column in [1,2,3,4]:
        assert len({r[column] for r in records})==len(records),'Duplicate production identity in cycle mapping'
    assert len({v for r in records for v in [r[3],r[4]]})==len(records)*2,'Reused stock identity across cycles'
    selections=[]
    for job,eid,wid,raw,fg,q in records:
        assert all(str(v).isdigit() for v in [job,eid,wid,raw,fg])
        qty=Decimal(q);assert qty>0
        selections.append(f'SELECT {job} job,{eid} eid,{wid} wid,{raw} raw_id,{fg} fg_id,{qty} qty')
    expected='WITH expected AS ('+' UNION ALL '.join(selections)+') '
    output={'database':w['expectedDb'],'factory':w['factory'],'completedCycles':len(records),'checks':{}}
    saved=[]
    def query(name,sql):
        sql=expected+sql;saved.append('-- '+name+'\n'+sql+';\n')
        r=subprocess.run(['mysql','--defaults-extra-file='+str(a.mysql_config),'--connect-timeout=10','--xml',w['expectedDb']],input=sql,text=True,capture_output=True,check=True)
        rows=[{f.get('name'):f.text for f in row} for row in ET.fromstring(r.stdout).findall('row')]
        return rows
    checks={
      'lifecycleAndSource':"SELECT COUNT(*) bad FROM expected x LEFT JOIN workorder w ON w.id=x.wid LEFT JOIN mes_production_execution e ON e.id=x.eid WHERE w.id IS NULL OR e.id IS NULL OR w.source IS NULL OR w.source<>'LOCAL' OR w.status<>'RELEASED' OR e.status<>'COMPLETED' OR e.workorder_id<>w.id OR e.product_id<>w.product_id OR e.routing_id<>w.routing_id OR w.planned_qty<>x.qty OR e.target_qty<>x.qty",
      'lockedPath':"SELECT COUNT(*) bad FROM expected x LEFT JOIN mes_execution_path p ON p.id=x.eid WHERE p.id IS NULL OR p.path_status<>'LOCKED' OR (SELECT COUNT(*) FROM mes_execution_path_node n WHERE n.execution_id=x.eid)<>5",
      'reportTotals':f"SELECT COUNT(*) bad FROM (SELECT x.eid,x.qty,COUNT(r.id) n,COALESCE(SUM(r.quantity_delta),0) q FROM expected x LEFT JOIN mes_production_report r ON r.execution_id=x.eid GROUP BY x.eid,x.qty HAVING n<>{5*w['batches']} OR q<>x.qty*5) z",
      'reportNodeTotals':f"SELECT COUNT(*) bad FROM (SELECT x.eid,n.routing_node_id,x.qty,COUNT(r.id) n,COALESCE(SUM(r.quantity_delta),0) q FROM expected x JOIN mes_execution_path_node n ON n.execution_id=x.eid LEFT JOIN mes_production_report r ON r.execution_id=x.eid AND r.routing_node_id=n.routing_node_id GROUP BY x.eid,n.routing_node_id,x.qty HAVING n<>{w['batches']} OR q<>x.qty) z",
      'reportIdentity':"SELECT COUNT(*) bad FROM expected x JOIN mes_production_report r ON r.execution_id=x.eid WHERE r.record_type<>'REPORT' OR r.reference_report_id IS NOT NULL OR r.quantity_delta<=0 OR r.operator_user_id<>"+w['operatorUserId']+" OR r.recorded_by<>"+w['operatorUserId'],
      'frozenConfirmedInput':"SELECT COUNT(*) bad FROM expected x LEFT JOIN mes_material_issue_input f ON f.execution_id=x.eid LEFT JOIN mes_material_issue i ON i.id=f.issue_id WHERE f.id IS NULL OR f.output_qty<>x.qty OR f.stock_id<>x.raw_id OR f.source_mode<>'LOCAL_ONLY' OR f.input_kind<>'PRODUCTION_INPUT' OR f.confirmed_at IS NULL OR f.stock_applied<>1 OR i.id IS NULL OR i.execution_id<>x.eid OR i.status<>'CONFIRMED'",
      'qualityRelease':"SELECT COUNT(*) bad FROM (SELECT x.eid,x.qty,COUNT(q.id) n,COALESCE(SUM(q.quantity),0) q FROM expected x LEFT JOIN mes_quality_release q ON q.execution_id=x.eid GROUP BY x.eid,x.qty HAVING n<>1 OR q<>x.qty) z",
      'handoverTotal':"SELECT COUNT(*) bad FROM (SELECT x.eid,x.qty,COUNT(r.id) n,COALESCE(SUM(r.quantity_delta),0) q FROM expected x LEFT JOIN mes_production_receipt r ON r.execution_id=x.eid GROUP BY x.eid,x.qty HAVING n<>2 OR q<>x.qty) z",
      'acceptanceTotal':"SELECT COUNT(*) bad FROM (SELECT x.eid,x.qty,COUNT(r.id) n,COALESCE(SUM(r.quantity_delta),0) q FROM expected x LEFT JOIN mes_warehouse_acceptance r ON r.execution_id=x.eid GROUP BY x.eid,x.qty HAVING n<>2 OR q<>x.qty) z",
      'acceptanceReferences':"SELECT COUNT(*) bad FROM expected x JOIN mes_warehouse_acceptance a ON a.execution_id=x.eid LEFT JOIN mes_production_receipt r ON r.id=a.receipt_id LEFT JOIN wm_stock_action s ON s.id=a.stock_action_id JOIN workorder w ON w.id=x.wid WHERE r.id IS NULL OR r.execution_id<>x.eid OR a.stock_id<>x.fg_id OR a.product_id<>w.product_id OR s.id IS NULL OR s.stock_id<>a.stock_id OR s.quantity_delta<>a.quantity_delta",
      'receiptSplitModel':"SELECT COUNT(*) bad FROM expected x JOIN mes_production_receipt r ON r.execution_id=x.eid WHERE r.quantity_delta NOT IN (x.qty*0.6,x.qty*0.4) OR r.reference_receipt_id IS NOT NULL",
      'receiptNotOverspent':"SELECT COUNT(*) bad FROM (SELECT r.id,r.quantity_delta,COALESCE(SUM(a.quantity_delta),0) accepted FROM expected x JOIN mes_production_receipt r ON r.execution_id=x.eid LEFT JOIN mes_warehouse_acceptance a ON a.receipt_id=r.id GROUP BY r.id,r.quantity_delta HAVING accepted<>r.quantity_delta) z",
      'completionFrozenQuantities':"SELECT COUNT(*) bad FROM expected x LEFT JOIN mes_execution_completion c ON c.execution_id=x.eid WHERE c.id IS NULL OR c.effective_qty<>x.qty OR c.released_qty<>x.qty OR c.receipt_qty<>x.qty OR c.accepted_qty<>x.qty",
      'stockBalances':"SELECT COUNT(*) bad FROM expected x LEFT JOIN wm_inventory raw ON raw.id=x.raw_id LEFT JOIN wm_inventory fg ON fg.id=x.fg_id JOIN workorder w ON w.id=x.wid WHERE raw.id IS NULL OR fg.id IS NULL OR raw.quantity<>0 OR raw.available_quantity<>0 OR fg.quantity<>x.qty OR fg.available_quantity<>x.qty OR fg.material_id<>w.product_id",
      'stockActionMath':"SELECT COUNT(*) bad FROM expected x JOIN wm_stock_action a ON a.stock_id=x.raw_id OR a.stock_id=x.fg_id WHERE a.quantity_after<>a.quantity_before+a.quantity_delta OR a.available_after<>a.available_before+a.available_delta OR a.quantity_before<0 OR a.quantity_after<0 OR a.available_before<0 OR a.available_after<0",
      'stockActionContinuity':"SELECT COUNT(*) bad FROM (SELECT a.stock_id,a.quantity_before,a.available_before,LAG(a.quantity_after,1,0) OVER(PARTITION BY a.stock_id ORDER BY a.occurred_at,a.id) previous_qty,LAG(a.available_after,1,0) OVER(PARTITION BY a.stock_id ORDER BY a.occurred_at,a.id) previous_available FROM expected x JOIN wm_stock_action a ON a.stock_id=x.raw_id OR a.stock_id=x.fg_id) z WHERE quantity_before<>previous_qty OR available_before<>previous_available",
      'rawMaterialUseAndStockLedger':"SELECT COUNT(*) bad FROM (SELECT x.job,x.qty,a.stock_id,COUNT(*) n,SUM(a.quantity_delta) delta,SUM(a.available_delta) avail,SUM(CASE WHEN a.quantity_delta<0 THEN -a.quantity_delta ELSE 0 END) consumed FROM expected x JOIN wm_stock_action a ON a.stock_id=x.raw_id OR a.stock_id=x.fg_id GROUP BY x.job,x.qty,a.stock_id) z JOIN expected x ON x.job=z.job WHERE n<>2 OR (z.stock_id=x.raw_id AND (delta<>0 OR avail<>0 OR consumed<>x.qty*2)) OR (z.stock_id=x.fg_id AND (delta<>x.qty OR avail<>x.qty OR consumed<>0))"
    }
    for name,sql in checks.items():
        rows=query(name,sql);bad=int(rows[0]['bad']);output['checks'][name]=bad
    counts={}
    for table in w['coreTables']:
        condition='t.stock_id=x.raw_id OR t.stock_id=x.fg_id' if table=='wm_stock_action' else 't.execution_id=x.eid'
        counts[table]=int(query('count-'+table,'SELECT COUNT(*) n FROM expected x JOIN '+table+' t ON '+condition)[0]['n'])
    output['coreFactCounts']=counts;output['coreFactTotal']=sum(counts.values());output['expectedCoreFacts']=len(records)*w['coreFactsPerCycle']
    output['expectedFinishedQuantity']=str(sum(Decimal(r[5]) for r in records))
    output['passed']=all(n==0 for n in output['checks'].values()) and output['coreFactTotal']==output['expectedCoreFacts'] and (a.partial or output['coreFactTotal']>=100000)
    output['fullAcceptanceComplete']=not a.partial and output['passed']
    suffix='audit-partial' if a.partial else 'audit'
    (a.directory/(suffix+'.sql')).write_text('\n'.join(saved));(a.directory/(suffix+'-results.json')).write_text(json.dumps(output,ensure_ascii=False,indent=2))
    print(json.dumps(output,ensure_ascii=False,indent=2))
    if not output['passed']:raise SystemExit('Related production facts audit failed')


if __name__=='__main__':main()
