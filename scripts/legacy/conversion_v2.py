"""Evidence-only V2 rules. Gross supplements belong to nodes, never employees."""
from collections import Counter, defaultdict
from dataclasses import asdict
from decimal import Decimal
import json

from report_driven import number, text, digest

RULE_VERSION = 'report-driven-local-v2'


def forge_isolation(data, orders):
    by_code = {w.code: w for w in orders}
    by_identity = {identity: w.code for w in orders for identity in w.raw_source_identities}
    routes = {text(r, 'route_id') for r in data['source:pro_route'] if str(r['forge_joining_flag']) == '1'}
    isolated = {w.code for w in orders if any(e.key[2] in routes for e in w.executions)}
    edges, links = [], []
    consumption = defaultdict(set)
    for r in data['source:pro_buckle']:
        consumption[(text(r, 'item_code'), text(r, 'material_xtransfer_no'))].add(text(r, 'material_doc_no'))
    for r in data['source:pro_route_broken_step']:
        upstream = by_identity.get(text(r, 'before_work_id'))
        if upstream is None and text(r, 'before_work_id') in by_code:
            upstream = text(r, 'before_work_id')
        if upstream is None:
            matches = {w.code for w in orders for e in w.executions if e.key[1] == text(r, 'before_lot_code')
                       and e.key[2] == text(r, 'before_route_id')}
        else:
            matches = {upstream}
        downstream = consumption[(text(r, 'after_item_code'), text(r, 'after_lot_code'))]
        isolated.update(matches | (downstream & by_code.keys()))
        for up in matches:
            for down in downstream:
                edges.append((up, down))
        links.append({'raw': r, 'upstream': sorted(matches), 'downstream': sorted(downstream),
                      'ambiguous': len(matches) != 1})
    while True:
        previous = set(isolated)
        for up, down in edges:
            if up in isolated or down in isolated:
                isolated.update((up, down))
        if isolated == previous:
            break
    return isolated, links


def convert_v2(data, orders, defects):
    isolated, links = forge_isolation(data, orders)
    quality = defaultdict(list)
    for defect in defects:
        r = defect.raw
        quality[(text(r, 'workorder_code'), text(r, 'xtransfer_no'), text(r, 'route_id'))].append(r)
    inputs = {text(r, 'record_id'): r for r in data['source:pro_material']}
    balances = defaultdict(list)
    for r in data.get('source:pro_reporting_work_material', []):
        balances[(text(r, 'workorder_code'), text(r, 'xtransfer_no'), text(r, 'router_id'))].append(r)
    processes = {text(r, 'record_id'): text(r, 'process_id') for r in data['source:pro_route_process']}
    result, forge = [], []
    for w in orders:
        out = {'workorder': asdict(w), 'executions': [], 'issues': list(w.issues), 'ruleVersion': RULE_VERSION}
        for e in w.executions:
            issues = list(e.issues)
            if any(r.raw.get('update_time') for r in e.reports):
                issues.append('REPORT_MODIFIED_WITHOUT_EVENT_HISTORY')
            material_codes, units = set(), set()
            for identity in e.input_evidence_ids:
                for part in json.loads(inputs[identity]['detail']):
                    material_codes.add(part['itemCode'])
                    if number(part['qtyPer']) != 1:
                        issues.append('NON_UNIT_RATIO_REQUIRES_REVIEW')
            if len(material_codes) != 1:
                issues.append('MATERIAL_IDENTITY_REQUIRES_REVIEW')
            for r in e.reports:
                units.add(text(r.raw, 'unit_of_measure'))
            remaining = {}
            for r in balances[e.key]:
                parts = json.loads(r['material'])
                if len(parts) != 1 or parts[0]['itemCode'] not in material_codes or number(parts[0]['qtyPer']) != 1:
                    issues.append('BALANCE_MATERIAL_OR_RATIO_CONFLICT')
                    continue
                q = number(parts[0]['acceptedQty'])
                if q < 0:
                    issues.append('NEGATIVE_MATERIAL_BALANCE')
                pid = text(r, 'process_id')
                if pid in remaining:
                    issues.append('DUPLICATE_NODE_BALANCE')
                remaining[pid] = q
            losses = defaultdict(Decimal)
            qrows = quality[e.key]
            qreasons = []
            for q in qrows:
                reasons = []
                if str(q['type']) not in ('5704', '5705'):
                    reasons.append('NITRIDING_OR_UNKNOWN_LOSS_SEMANTICS')
                if str(q['status']) not in ('0', '1') or number(q['quantity']) <= 0:
                    reasons.append('NON_EFFECTIVE_OR_INVALID_DEFECT_STATE')
                if text(q, 'material_item_code') not in material_codes:
                    reasons.append('DEFECT_NOT_PRIMARY_INPUT_MATERIAL')
                if text(q, 'unit_of_measure') not in units:
                    reasons.append('DEFECT_UNIT_CONFLICT')
                if text(q, 'process_id') not in e.reported_by_node:
                    reasons.append('DEFECT_NODE_HAS_NO_REPORT')
                if text(q, 'workorder_id') not in w.raw_source_identities:
                    reasons.append('DEFECT_WORKORDER_IDENTITY_CONFLICT')
                # Status 0/1 describes scrap conversion. Both still consume the local
                # material balance in the inspected legacy implementation.
                if not reasons:
                    losses[text(q, 'process_id')] += number(q['quantity'])
                qreasons.append({'raw': q, 'issues': reasons})
                issues.extend(reasons)
            upstream = number(e.input_qty_candidate) if e.input_qty_candidate else None
            nodes = []
            for source_node in e.routing.current_node_ids:
                pid = processes[source_node]
                if pid not in e.reported_by_node:
                    continue
                net = number(e.reported_by_node[pid])
                loss = losses[pid]
                gross = net + loss
                rest = remaining.get(pid)
                conserved = upstream is not None and rest is not None and rest >= 0 and gross + rest == upstream
                if not conserved:
                    issues.append('NODE_QUANTITY_NOT_CONSERVED')
                nodes.append({'sourceNode': source_node, 'processId': pid,
                              'legacyNet': str(net), 'gross': str(gross), 'effectiveDefect': str(loss),
                              'legacyRemaining': str(rest) if rest is not None else None,
                              'effective': str(net), 'conserved': conserved,
                              'allocation': 'NODE_AGGREGATE_ONLY_NO_EMPLOYEE_UPLIFT'})
                upstream = net
            if w.code in isolated:
                issues.append('FORGE_DEPENDENCY_COMPONENT_EXCLUDED')
            out['executions'].append({'key': e.key, 'nodes': nodes, 'quality': qreasons,
                                      'issues': sorted(set(issues))})
        if w.code in isolated:
            out['issues'].append('FORGE_DEPENDENCY_COMPONENT_EXCLUDED')
            forge.append(out)
        else:
            result.append(out)
    clean = [w for w in result if not w['issues'] and all(not e['issues'] for e in w['executions'])]
    reasons = Counter(i for w in result for i in w['issues'])
    reasons.update(i for w in result for e in w['executions'] for i in e['issues'])
    summary = {'ruleVersion': RULE_VERSION, 'ordinaryWorkorders': len(result),
               'ordinaryExecutions': sum(len(w['executions']) for w in result),
               'excludedForgeWorkorders': len(forge), 'excludedForgeExecutions': sum(len(w['executions']) for w in forge),
               'excludedUnresolvedDependencyWorkorders': sorted(isolated - {w.code for w in orders}),
               'structurallyQuantityEligibleWorkorders': len(clean),
               'structurallyQuantityEligibleExecutions': sum(len(w['executions']) for w in clean),
               'structurallyQuantityEligibleReports': sum(len(e['reports']) for w in clean for e in w['workorder']['executions']),
               'quantityConflictExecutions': sum('NODE_QUANTITY_NOT_CONSERVED' in e['issues'] for w in result for e in w['executions']),
               'reasonCounts': dict(reasons), 'targetMasterMappingApproved': False, 'batchGo': False}
    return result, forge, links, summary
