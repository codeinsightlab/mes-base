#!/usr/bin/env python3
"""Freeze one reviewed ordinary component or explicitly selected cards. Never writes DB."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
from report_driven import canonical, digest
from prepare_sample import verify_account


def build_command(component, source, target_users, units, snapshot_hash, batch_id, factory_id, selected_keys=None):
    w=component['workorder']
    if component['issues']:raise ValueError('Unresolved workorder')
    items={r['item_code']:r for r in source['md_item']}
    processes={r['process_id']:r for r in source['pro_process']}
    users={r['user_id']:r for r in source['sys_user']}
    source_logins=Counter(u['user_name'] for u in source['sys_user'])
    target_logins=Counter(u['user_name'] for u in target_users.values())
    product=items[w['product_codes'][0]]
    unit=next(u for u in units if u['code']==product['unit_of_measure'])
    c={'batchId':batch_id,'snapshotHash':snapshot_hash,'factoryId':factory_id,'code':w['code'],
       'sourceHash':digest(w),'sourcePayload':canonical(w),'plannedQty':w['planned_qty_candidate'],
       'plannedStart':w['planned_start_at'].replace(' ','T'),'plannedEnd':w['planned_end_at'].replace(' ','T'),
       'unitCode':unit['code'],'unitName':unit['name'],'productCode':product['item_code'],
       'productName':product['item_name'],'specification':product['specification'],'executions':[]}
    for e,plan in zip(w['executions'],component['executions']):
        key=canonical(e['key'])
        if selected_keys is not None and key not in selected_keys:continue
        if plan['issues']:raise ValueError('Unresolved execution: '+','.join(plan['issues']))
        dst={'sourceKey':key,'sourceHash':digest(e),'sourcePayload':canonical(e),'inputQty':e['input_qty_candidate'],'nodes':[],'reports':[],'defects':[]}
        for n in plan['nodes']:
            pr=processes[n['processId']]
            dst['nodes'].append({'processId':n['processId'],'code':pr['process_code'],'name':pr['process_name'],'legacyNet':n['legacyNet'],
                                'grossCorrection':n['effectiveDefect'],'effectiveDefect':n['effectiveDefect'],
                                'evidence':canonical({'quantity':n,'defects':[q for q in plan['quality'] if q['raw']['process_id']==n['processId']]})})
        for r in e['reports']:
            raw=r['raw'];old=users.get(raw['user_id']);new=target_users.get(raw['user_id'])
            if old is None or new is None:raise ValueError('ACCOUNT_MISSING')
            if source_logins[old['user_name']]!=1 or target_logins[new['user_name']]!=1:
                raise ValueError('ACCOUNT_LOGIN_NOT_UNIQUE')
            verify_account(old,new)
            if old['create_time']>raw['create_time']:raise ValueError('ACCOUNT_CREATED_AFTER_REPORT')
            if raw.get('user_name') and raw['user_name']!=old['user_name']:
                raise ValueError('HISTORICAL_REPORT_LOGIN_REQUIRES_REVIEW')
            dst['reports'].append({'sourceId':r['mapping']['source_pk'],'sourceHash':r['mapping']['source_hash'],
                                  'sourcePayload':canonical({'report':raw,'sourceUser':old,'targetUser':new}),
                                  'processId':raw['process_id'],'quantity':raw['reporting_work_quantity'],
                                  'operatorUserId':raw['user_id'],'reportTime':raw['create_time'].replace(' ','T')})
        dst['defects']=[{'sourceId':q['raw']['record_id'],'sourceHash':digest(q['raw']),'sourcePayload':canonical(q['raw'])} for q in plan['quality']]
        c['executions'].append(dst)
    if not c['executions']:raise ValueError('Empty approved card scope')
    if len(c['executions'])>100 or sum(len(e['reports']) for e in c['executions'])>10000:raise ValueError('COMPONENT_EXCEEDS_BOUNDED_COMMAND')
    return c


def main():
    os.umask(0o077);p=argparse.ArgumentParser(description=__doc__)
    for name in ('evidence','reference','selection','output'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();selection=json.loads(a.selection.read_text());source={};target={};units=[json.loads(l) for l in (a.evidence/'units.jsonl').open()]
    for l in a.reference.open():
        x=json.loads(l);kind,table=x['dataset'].split(':');(source if kind=='source' else target).setdefault(table,[]).append(x['row'])
    component=next(json.loads(l) for l in (a.evidence/'ordinary-v2.jsonl').open() if json.loads(l)['workorder']['code']==selection['code'])
    c=build_command(component,source,{r['user_id']:r for r in target['sys_user']},units,json.loads((a.evidence/'batch.json').read_text())['snapshot_hash'],selection['batchId'],selection['factoryId'],selection.get('keys'))
    a.output.write_text(canonical(c)+'\n');print(json.dumps({'executions':len(c['executions']),'reports':sum(len(e['reports']) for e in c['executions']),'commandHash':digest(c)}))


if __name__=='__main__':main()
