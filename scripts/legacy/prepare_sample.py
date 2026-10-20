#!/usr/bin/env python3
"""Build a bounded reviewed S1/S2 command from the V2 frozen evidence. No database access."""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
from report_driven import canonical, digest


def verify_account(old, new, proof=None):
    if old is None or new is None or any(old[k] != new[k] for k in ('user_name','nick_name')):
        raise ValueError('No verified frozen account identity match')
    if old.get('create_time') and new.get('create_time') == old['create_time']:
        return
    if proof and str(proof['sourceUserId']) == str(old['user_id']) and str(proof['targetUserId']) == str(new['user_id']) \
            and proof['sourceCreatedAt'] is not None and proof['creationMatch'] == 1 \
            and proof['sourceLoginUnique'] == proof['targetLoginUnique'] == 1 \
            and proof['loginMatch'] == proof['nameMatch'] == 1:
        return
    raise ValueError('Missing unique login and creation identity evidence')


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--evidence',type=Path,required=True)
    p.add_argument('--aliases',type=Path,required=True)
    p.add_argument('--sample',choices=('S1','S2'),required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--account-proof',type=Path)
    a=p.parse_args()
    proofs = {str(r["sourceUserId"]):r for r in (json.loads(l) for l in a.account_proof.open())} if a.account_proof else {}
    alias=json.loads(a.aliases.read_text())[a.sample]
    code=alias['order']['workorder_code']
    selected=None
    for line in (a.evidence/'ordinary-v2.jsonl').open():
        w=json.loads(line)
        if w['workorder']['code']==code:selected=w;break
    if selected is None or selected['issues']:raise ValueError('Sample is not in the eligible ordinary range')
    w=selected['workorder']
    source_users={};target_users={};items={};processes={}
    for line in (a.evidence/'before/snapshot.jsonl').open():
        row=json.loads(line);r=row['row'];kind=row['dataset']
        if kind=='source:sys_user':source_users[str(r['user_id'])]=r
        if kind=='target:sys_user':target_users[str(r['user_id'])]=r
        if kind=='source:md_item':items[str(r['item_code'])]=r
        if kind=='source:pro_process':processes[str(r['process_id'])]=r
    product=items[w['product_codes'][0]]
    unit=next(json.loads(line) for line in (a.evidence/'units.jsonl').open() if json.loads(line)['code']==product['unit_of_measure'])
    command={'batchId':'LEGACY-V2-'+a.sample+'-20261020','snapshotHash':json.loads((a.evidence/'batch.json').read_text())['snapshot_hash'],
             'factoryId':'TEST_DEV_PERF_20261019','code':code,'sourceHash':digest(w),'sourcePayload':canonical(w),
             'plannedQty':w['planned_qty_candidate'],'plannedStart':w['planned_start_at'].replace(' ','T'),
             'plannedEnd':w['planned_end_at'].replace(' ','T'),'unitCode':unit['code'],'unitName':unit['name'],
             'productCode':product['item_code'],'productName':product['item_name'],
             'specification':product['specification'],'executions':[]}
    for e,plan in zip(w['executions'],selected['executions']):
        if a.sample=='S1' and e['key'][1]!=alias['batches']['S1-B01']:continue
        if plan['issues']:raise ValueError('Unresolved sample execution')
        target={'sourceKey':canonical(e['key']),'sourceHash':digest(e),'sourcePayload':canonical(e),
                'inputQty':e['input_qty_candidate'],'nodes':[],'reports':[],'defects':[]}
        for n in plan['nodes']:
            process=processes[n['processId']]
            target['nodes'].append({'processId':n['processId'],'code':process['process_code'],'name':process['process_name'],
                                    'legacyNet':n['legacyNet'],'grossCorrection':n['effectiveDefect'],
                                    'effectiveDefect':n['effectiveDefect'],'evidence':canonical({'quantity':n,'defects':[q for q in plan['quality'] if q['raw']['process_id']==n['processId']]})})
        for r in e['reports']:
            raw=r['raw'];identity=str(raw['user_id'])
            old=source_users.get(identity);new=target_users.get(identity)
            verify_account(old, new, proofs.get(identity))
            target['reports'].append({'sourceId':r['mapping']['source_pk'],'sourceHash':r['mapping']['source_hash'],
                                      'sourcePayload':canonical({'report':raw,'sourceUser':old,'targetUser':new}),
                                      'processId':raw['process_id'],'quantity':raw['reporting_work_quantity'],
                                      'operatorUserId':identity,'reportTime':raw['create_time'].replace(' ','T')})
        for q in plan['quality']:
            target['defects'].append({'sourceId':q['raw']['record_id'],'sourceHash':digest(q['raw']),'sourcePayload':canonical(q['raw'])})
        command['executions'].append(target)
    if not command['executions']:raise ValueError('No selected execution')
    with a.output.open('x') as out:out.write(canonical(command)+'\n')
    print(json.dumps({'sample':a.sample,'executions':len(command['executions']),'reports':sum(len(e['reports']) for e in command['executions']),
                      'defects':sum(len(e['defects']) for e in command['executions']),'commandHash':digest(command)}))


if __name__=='__main__':main()
