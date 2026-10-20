"""Synthetic conversion boundary tests; no network or target writes."""
from dataclasses import asdict
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('legacy_report_driven', Path(__file__).parents[1] / 'legacy/report_driven.py')
M = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = M
SPEC.loader.exec_module(M)


def dataset():
    rows = [dict(record_id=str(i), workorder_code='TEST-WO', workorder_id='OLD-MO',
                 xtransfer_no='CARD', route_id='1', process_id=str(i), process_code='P'+str(i),
                 item_code='PRODUCT', unit_of_measure='PCS', reporting_work_quantity='400',
                 reporting_work_type='SELF', user_id='12', create_time='2024-01-01 10:00:00') for i in (1, 2)]
    return {'source:pro_reporting_work': rows,
            'source:pro_route': [dict(route_id='1', forge_joining_flag='0')],
            'source:pro_route_process': [dict(record_id=str(i+10), route_id='1', process_id=str(i),
                                             order_num=str(i)) for i in (1, 2)],
            'source:pro_material': [dict(record_id='1', doc_no='TEST-WO', xtransfer_no='CARD',
                route_id='1', process_id='1', issue_doc_no='OLD-ISSUE',
                detail='[{"itemCode":"RAW","acceptedQty":400,"qtyPer":1}]')],
            'source:pro_route_product': [dict(route_id='1', item_code='PRODUCT')],
            'source:pro_process': [dict(process_id=str(i), process_code='P'+str(i)) for i in (1, 2)],
            'target:workorder': [], 'source:qc_defective': []}


class ConversionTest(unittest.TestCase):
    def test_missing_old_head_is_not_rejected_and_equal_plan_times_are_preserved(self):
        orders, _ = M.convert(dataset())
        self.assertEqual([], orders[0].issues)
        self.assertEqual([], orders[0].executions[0].issues)
        self.assertEqual('400', orders[0].planned_qty_candidate)
        self.assertEqual(orders[0].planned_start_at, orders[0].planned_end_at)
        self.assertIsNone(orders[0].target_id)

    def test_uni_allocations_are_summed_once(self):
        data = dataset()
        data['source:pro_reporting_work'][0].update(reporting_work_type='UNI', reporting_work_quantity='200')
        extra = dict(data['source:pro_reporting_work'][0], record_id='3', user_id='13')
        data['source:pro_reporting_work'].append(extra)
        orders, defects = M.convert(data)
        self.assertEqual('400', orders[0].executions[0].reported_by_node['1'])
        self.assertEqual('400', M.summary(orders, defects)['uniAllocatedQuantity'])

    def test_plan_is_not_sum_of_all_operations(self):
        orders, _ = M.convert(dataset())
        self.assertEqual('400', orders[0].planned_qty_candidate)

    def test_identity_conflict_is_explicit(self):
        data = dataset()
        data['source:pro_reporting_work'][1]['workorder_id'] = 'OTHER'
        self.assertIn('SOURCE_IDENTITY_CONFLICT', M.convert(data)[0][0].issues)

    def test_target_collision_is_not_overwritten(self):
        data = dataset()
        data['target:workorder'] = [dict(code='TEST-WO')]
        self.assertIn('TARGET_CODE_CONFLICT', M.convert(data)[0][0].issues)

    def test_duplicate_process_identity_never_maps_by_name(self):
        data = dataset()
        data['source:pro_route_process'].append(dict(record_id='99', route_id='1', process_id='1', order_num='3'))
        report = M.convert(data)[0][0].executions[0].reports[0]
        self.assertIsNone(report.node_candidate)
        self.assertIn('MISSING_OR_AMBIGUOUS_ROUTE_PROCESS', report.issues)

    def test_defect_does_not_double_deduct_or_create_extra_report(self):
        data = dataset()
        data['source:pro_reporting_work'][1]['reporting_work_quantity'] = '396'
        data['source:qc_defective'] = [dict(record_id='9', type='5705', quantity='4')]
        orders, defects = M.convert(data)
        self.assertEqual('396', orders[0].executions[0].reported_by_node['2'])
        self.assertEqual(2, len(orders[0].executions[0].reports))
        self.assertEqual('4', defects[0].raw['quantity'])
        self.assertEqual('PENDING_QUANTITY_SEMANTICS', defects[0].status)

    def test_missing_or_negative_input_rejects_plan(self):
        data = dataset()
        data['source:pro_material'] = []
        self.assertIsNone(M.convert(data)[0][0].planned_qty_candidate)
        data = dataset()
        data['source:pro_material'][0]['detail'] = '[{"itemCode":"RAW","acceptedQty":-1,"qtyPer":1}]'
        self.assertIsNone(M.convert(data)[0][0].planned_qty_candidate)

    def test_duplicate_issue_fails_closed(self):
        data = dataset()
        data['source:pro_material'].append(dict(data['source:pro_material'][0], record_id='2'))
        self.assertIn('DUPLICATE_OR_MISSING_ISSUE_IDENTITY', M.convert(data)[0][0].executions[0].issues)

    def test_mapping_identity_stable_across_batch_and_source_change(self):
        first = M.mapping('pro_reporting_work', dict(record_id='1', quantity='1'))
        changed = M.mapping('pro_reporting_work', dict(record_id='1', quantity='2'))
        self.assertEqual(first.command_key, changed.command_key)
        self.assertNotEqual(first.source_hash, changed.source_hash)
        with patch.object(M, 'RULE_VERSION', 'future-v2'):
            self.assertEqual(first.command_key, M.mapping('pro_reporting_work', dict(record_id='1')).command_key)
        data = dataset()
        self.assertEqual([asdict(w) for w in M.convert(data)[0]], [asdict(w) for w in M.convert(data)[0]])

    def test_existing_field_change_and_delete_are_detected(self):
        old = {'baseline:table': [dict(pk=[1], hash='old'), dict(pk=[2], hash='same')]}
        new = {'baseline:table': [dict(pk=[1], hash='changed'), dict(pk=[3], hash='new')]}
        result = M.protection(old, new)
        self.assertFalse(result['allExistingRowsUnchanged'])
        self.assertEqual(1, result['changed']['baseline:table'])
        self.assertEqual(1, result['removed']['baseline:table'])
        self.assertEqual(1, result['added']['baseline:table'])

    def test_incomplete_route_does_not_invent_reports(self):
        data = dataset()
        data['source:pro_reporting_work'].pop()
        orders, _ = M.convert(data)
        self.assertEqual(1, len(orders[0].executions[0].reports))
        self.assertEqual(['1'], orders[0].executions[0].routing.observed_process_ids)

    def test_wrong_instance_is_rejected_before_business_reads(self):
        with patch.object(M, 'mysql', return_value='{"uuid":"wrong","port":3306}\n') as client:
            with self.assertRaisesRegex(ValueError, 'no business tables read'):
                M.capture(Path('/unused'), Path('/unused'))
            self.assertEqual(1, client.call_count)

    def test_empty_table_append_is_detected(self):
        before = {'baseline:empty': []}
        after = {'baseline:empty': [dict(pk=[1], hash='new')]}
        self.assertEqual(1, M.protection(before, after)['added']['baseline:empty'])

    def test_changed_target_schema_is_detected(self):
        before = {'baseline:t': [], 'targetMetadata': [{'column': 'old'}]}
        after = {'baseline:t': [], 'targetMetadata': [{'column': 'new'}]}
        self.assertFalse(M.protection(before, after)['allExistingRowsUnchanged'])


if __name__ == '__main__':
    unittest.main()
