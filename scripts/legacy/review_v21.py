#!/usr/bin/env python3
"""Read-only admission review of the frozen V2 ordinary components; never grants approval."""
import argparse
from collections import Counter,defaultdict
from decimal import Decimal
import json
import os
from pathlib import Path
from report_driven import canonical,digest,SERVER,SOURCE
from prepare_component import build_command


def audit_component(w,source,target,units,snapshot):
    reasons=set(w['issues'])|{r for e in w['executions'] for r in e['issues']}
    hard={'REPORT_MODIFIED_WITHOUT_EVENT_HISTORY','NEGATIVE_MATERIAL_BALANCE','NON_EFFECTIVE_OR_INVALID_DEFECT_STATE','NITRIDING_OR_UNKNOWN_LOSS_SEMANTICS','NODE_QUANTITY_NOT_CONSERVED'}
    if reasons:return ('REJECTED' if reasons&hard else 'NEEDS_REVIEW'),sorted(reasons),None,[]
    try:c=build_command(w,source,{r['user_id']:r for r in target['sys_user']},units,snapshot,'PROPOSED-V21-'+digest(w['workorder']['code'])[:24],'TEST_DEV_PERF_20261019')
    except (ValueError,KeyError,StopIteration) as e:return 'NEEDS_REVIEW',['COMMAND_OR_ACCOUNT_REVIEW:'+str(e)],None,[]
    if any([n['processId'] for n in e['nodes']]!=[n['processId'] for n in c['executions'][0]['nodes']] for e in c['executions']):reasons.add('ACTUAL_PATH_SET_MISMATCH')
    mappings={r['id']:r for r in target.get('mes_legacy_source_mapping',[])};manifest=[]
    def resolve(kind,pk,table,code,semantic):
        identity=digest([SERVER,SOURCE,kind,pk]);mapped=mappings.get(identity);matches=[r for r in target[table] if r['code']==code]
        record={'sourceKind':kind,'sourcePk':pk,'sourceIdentity':identity,'targetKind':table,'targetCode':code,'semanticHash':digest(semantic),'targetId':None,'action':'CREATE_AFTER_APPROVAL'}
        if mapped:
            if mapped['source_hash']!=digest(semantic) or mapped['rule_version']!='report-driven-local-v2' or len(matches)!=1 or str(matches[0]['id'])!=str(mapped['entity_id']):reasons.add('TARGET_MAPPING_CONFLICT:'+table)
            else:
                record.update(targetId=str(mapped['entity_id']),action='REUSE_REVIEWED_MAPPING')
                if matches[0].get('name')!=semantic[-1 if table=='operation' else 1]:reasons.add('TARGET_CURRENT_NAME_CONFLICT:'+table)
                if table=='material':
                    unit_plan=next((m for m in manifest if m['targetKind']=='unit_measure'),None)
                    if unit_plan is None or str(matches[0].get('unit_id'))!=str(unit_plan['targetId']) or matches[0].get('specification')!=c['specification']:
                        reasons.add('TARGET_CURRENT_PRODUCT_UNIT_OR_SPEC_CONFLICT')
        elif matches:reasons.add('TARGET_CODE_REQUIRES_SEMANTIC_APPROVAL:'+table)
        manifest.append(record)
    resolve('UNIT',c['unitCode'],'unit_measure',c['unitCode'],[c['unitCode'],c['unitName']])
    resolve('MATERIAL',c['productCode'],'material',c['productCode'],[c['productCode'],c['productName'],c['unitCode']])
    seen=set()
    for e in c['executions']:
        for n in e['nodes']:
            if n['processId'] not in seen:resolve('PROCESS',n['processId'],'operation',n['code'],[n['processId'],n['code'],n['name']]);seen.add(n['processId'])
        for r in e['reports']:
            if Decimal(r['quantity']).as_tuple().exponent < -6:reasons.add('QUANTITY_PRECISION_REVIEW')
        for kind,pk,h in [('EXECUTION',e['sourceKey'],e['sourceHash']),*[("pro_reporting_work",r['sourceId'],r['sourceHash']) for r in e['reports']]]:
            m=mappings.get(digest([SERVER,SOURCE,kind,pk]))
            if m and (m['source_hash']!=h or m['rule_version']!='report-driven-local-v2'):reasons.add('SOURCE_MAPPING_CONFLICT:'+kind)
    existing=mappings.get(digest([SERVER,SOURCE,'WORKORDER',c['code']]))
    collisions=[r for r in target['workorder'] if r['code']==c['code']]
    if existing:
        if existing['source_hash']!=c['sourceHash'] or existing['rule_version']!='report-driven-local-v2' or len(collisions)!=1 or str(collisions[0]['id'])!=str(existing['entity_id']):reasons.add('WORKORDER_MAPPING_CONFLICT')
    elif collisions:reasons.add('WORKORDER_CODE_COLLISION')
    if len(set(e['sourceKey'] for e in c['executions']))!=len(c['executions']):reasons.add('DUPLICATE_EXECUTION_IDENTITY')
    if len(c['executions'])>100:reasons.add('FULL_COMPONENT_EXCEEDS_BOUND')
    return ('NEEDS_REVIEW' if reasons else 'APPROVABLE'),sorted(reasons),c,manifest


def main():
    os.umask(0o077);p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--evidence',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    a=p.parse_args();a.output.mkdir(mode=0o700);(a.output/'commands').mkdir();source=defaultdict(list);target=defaultdict(list)
    for l in a.reference.open():
        x=json.loads(l);kind,t=x['dataset'].split(':');(source if kind=='source' else target)[t].append(x['row'])
    units=[json.loads(l) for l in (a.evidence/'units.jsonl').open()];snapshot=json.loads((a.evidence/'batch.json').read_text())['snapshot_hash'];counts=defaultdict(Counter);rows=[]
    for l in (a.evidence/'ordinary-v2.jsonl').open():
        w=json.loads(l);status,reasons,c,manifest=audit_component(w,source,target,units,snapshot);raw=w['workorder'];n=sum(len(e['reports']) for e in raw['executions'])
        counts[status].update(workorders=1,executions=len(raw['executions']),reports=n)
        entry={'code':raw['code'],'status':status,'reasons':reasons,'componentHash':digest(w),'sourceSnapshotHash':snapshot,'ruleVersion':'report-driven-local-v2','targetMappings':manifest,'executionCount':len(raw['executions']),'reportCount':n,'approvedForExecution':False}
        if c:
            entry['commandHash']=digest(c);entry['commandFile']='commands/'+digest(raw['code'])+'.json';(a.output/entry['commandFile']).write_text(canonical(c))
        rows.append(entry)
    for l in (a.evidence/'forge-excluded-v2.jsonl').open():
        w=json.loads(l);raw=w['workorder'];n=sum(len(e['reports']) for e in raw['executions']);counts['FORGE_EXCLUDED'].update(workorders=1,executions=len(raw['executions']),reports=n)
        rows.append({'code':raw['code'],'status':'FORGE_EXCLUDED','reasons':['FORGE_DEPENDENCY_COMPONENT_EXCLUDED'],'componentHash':digest(w),'approvedForExecution':False})
    (a.output/'components.jsonl').write_text(''.join(canonical(r)+'\n' for r in rows))
    result={'counts':dict(counts),'sourceSnapshotHash':snapshot,'targetReferenceHash':digest(a.reference.read_text()),'approvedForExecution':False,'scope':'frozen V2 source admission only; no bulk execution'}
    (a.output/'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result['counts']))


if __name__=='__main__':main()
