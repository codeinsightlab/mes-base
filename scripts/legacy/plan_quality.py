"""Freeze reviewed legacy defects as normal declarations without duplicating opening loss.

This module plans only. It never writes a database or infers missing treatment/person facts.
"""
import argparse
from collections import defaultdict, Counter
from decimal import Decimal
import gzip
import hashlib
import json
from pathlib import Path
import uuid

RULE = 'legacy-quality-formal-v1'


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def key(original):
    return hashlib.sha256((RULE + ':' + original).encode()).hexdigest()


def identity(original):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, RULE + ':' + original))


def build(rows, reporter_overrides=None, allow_pending_disposition=False):
    # Explicitly reviewed historical identity mappings; never default to the operator.
    reporter_overrides = reporter_overrides or {}
    users = defaultdict(set)
    nodes = defaultdict(dict)
    quality = defaultdict(list)
    for row in rows:
        if row['kind'] == 'user' and row['name']:
            users[row['name']].add(row['id'])
        elif row['kind'] == 'node':
            process = row['sourceKey'].rsplit('/', 1)[1]
            if process in nodes[row['parentId']]:
                raise ValueError('Duplicate source node')
            nodes[row['parentId']][process] = row
        elif row['kind'] == 'quality':
            quality[row['parentId']].append(row)
    accepted, rejected = [], []
    for parent, group in sorted(quality.items()):
        reasons, declarations = set(), []
        totals = defaultdict(Decimal)
        for q in group:
            raw = q['raw']
            process = str(raw['process_id'])
            qty = Decimal(raw['quantity'])
            if raw['type'] not in ('5704', '5705') or qty <= 0:
                reasons.add('INVALID_LOSS')
            scrap_proven = raw['status'] == '1' and bool(raw.get('scrap_code'))
            # Missing or contradictory disposition evidence remains pending; never invent scrap.
            pending = raw['status'] in ('0', '1') and not scrap_proven
            if not scrap_proven and not (allow_pending_disposition and pending):
                reasons.add('DISPOSITION_NOT_PROVEN')
            creator = raw.get('create_by')
            creators = ({str(reporter_overrides[creator])} if creator in reporter_overrides
                        else users.get(creator, set()))
            if len(creators) != 1:
                reasons.add('REPORTER_NOT_UNIQUE')
            node = nodes[parent].get(process)
            if node is None:
                reasons.add('AFFECTED_NODE_MISSING')
            if not raw.get('cause') or len(raw['cause']) > 500 or not raw.get('create_time'):
                reasons.add('DECLARATION_FACT_MISSING')
            source_key = json.loads(q['executionKey'])
            if [raw['workorder_code'], raw['xtransfer_no'], str(raw['route_id'])] != source_key:
                reasons.add('SOURCE_IDENTITY_CONFLICT')
            totals[process] += qty
            responsible = nodes[parent].get(str(raw.get('responsible_process_id')))
            if str(raw.get('responsible_route_id')) != source_key[2]:
                responsible = None
            declarations.append(dict(source=q, declarationId=identity(q['mappingId']),
                                     reporterId=next(iter(creators)) if len(creators) == 1 else None,
                                     disposition='SCRAPPED' if scrap_proven else 'PENDING',
                                     affectedNodeId=node['nodeId'] if node else None,
                                     responsibleNodeId=responsible['nodeId'] if responsible else None,
                                     wasteCategory='PROCESS' if raw['type'] == '5705' else 'MATERIAL'))
        for process, node in nodes[parent].items():
            loss, gross = Decimal(str(node['loss'])), Decimal(str(node['gross']))
            if loss != totals.get(process, Decimal(0)) or gross != loss:
                reasons.add('OPENING_LOSS_NOT_CONSERVED')
        if reasons:
            rejected.append(dict(executionId=group[0]['executionId'], sourceIds=[q['sourceId'] for q in group],
                                 reasons=sorted(reasons)))
            continue
        accepted.append(dict(executionId=group[0]['executionId'], parentId=parent,
                             declarations=declarations,
                             nodes=[n for n in nodes[parent].values() if Decimal(str(n['loss'])) > 0]))
    return dict(rule=RULE, accepted=accepted, rejected=rejected,
                sourceCount=sum(len(q) for q in quality.values()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence', type=Path)
    args = parser.parse_args()
    with gzip.open(args.evidence / 'preflight.jsonl.gz', 'rt') as f:
        plan = build(json.loads(line) for line in f)
    content = canonical(plan)
    with gzip.open(args.evidence / 'plan.json.gz', 'wt') as f:
        f.write(content)
    (args.evidence / 'plan.sha256').write_text(hashlib.sha256(content.encode()).hexdigest() + '\n')
    counts = Counter(reason for r in plan['rejected'] for reason in r['reasons'])
    summary = dict(sourceCount=plan['sourceCount'], executions=len(plan['accepted']),
                   declarations=sum(len(e['declarations']) for e in plan['accepted']),
                   convertedNodes=sum(len(e['nodes']) for e in plan['accepted']),
                   rejectedExecutions=len(plan['rejected']),
                   rejectedSourceRows=sum(len(e['sourceIds']) for e in plan['rejected']),
                   reasons=dict(counts))
    (args.evidence / 'plan-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    (args.evidence / 'rejected.json').write_text(json.dumps(plan['rejected'], ensure_ascii=False, indent=2))
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
