import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'legacy'))
from plan_quality import build


class QualityPlanTest(unittest.TestCase):
    def rows(self):
        return [dict(kind='user', id='7', name='原申报人'),
                dict(kind='node', parentId='e', mappingId='n', nodeId=11,
                     sourceKey='["W","B","R"]/P', net='396', gross='4', loss='4'),
                dict(kind='quality', parentId='e', mappingId='q', sourceId='1',
                     sourceHash='f' * 64, executionId=1, executionKey='["W","B","R"]',
                     raw=dict(process_id='P', route_id='R', workorder_code='W', xtransfer_no='B',
                              quantity='4', type='5705', status='1', scrap_code='SCRAP-1',
                              create_by='原申报人', create_time='2026-01-01 01:00:00', cause='原缺陷'))]

    def test_converts_once_and_retains_identity(self):
        p = build(self.rows())
        self.assertEqual(1, len(p['accepted']))
        d = p['accepted'][0]['declarations'][0]
        self.assertEqual('7', d['reporterId'])
        self.assertEqual('PROCESS', d['wasteCategory'])
        self.assertEqual(d['declarationId'], build(self.rows())['accepted'][0]['declarations'][0]['declarationId'])
        self.assertEqual('4', p['accepted'][0]['nodes'][0]['gross'])

    def test_pending_scrap_excludes_entire_execution(self):
        rows = self.rows()
        rows[-1]['raw']['status'] = '0'
        self.assertEqual([], build(rows)['accepted'])
        self.assertIn('DISPOSITION_NOT_PROVEN', build(rows)['rejected'][0]['reasons'])

    def test_ambiguous_reporter_is_not_replaced_with_operator(self):
        rows = self.rows() + [dict(kind='user', id='8', name='原申报人')]
        self.assertIn('REPORTER_NOT_UNIQUE', build(rows)['rejected'][0]['reasons'])

    def test_quantity_mismatch_does_not_partially_import(self):
        rows = self.rows()
        rows[-1]['raw']['quantity'] = '3'
        self.assertIn('OPENING_LOSS_NOT_CONSERVED', build(rows)['rejected'][0]['reasons'])

    def test_wrong_batch_is_not_assigned_to_execution(self):
        rows = self.rows()
        rows[-1]['raw']['xtransfer_no'] = 'OTHER'
        self.assertIn('SOURCE_IDENTITY_CONFLICT', build(rows)['rejected'][0]['reasons'])


if __name__ == '__main__':
    unittest.main()
