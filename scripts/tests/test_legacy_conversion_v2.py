import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).parents[1] / 'legacy'))
from report_driven import convert
from conversion_v2 import convert_v2
from scripts.tests.test_legacy_report_driven import dataset


def source():
    d=dataset()
    d['source:pro_route_broken_step']=[]
    d['source:pro_buckle']=[]
    d['source:pro_reporting_work_material']=[dict(workorder_code='TEST-WO',xtransfer_no='CARD',router_id='1',process_id=str(i),material='[{"itemCode":"RAW","acceptedQty":0,"qtyPer":1}]') for i in (1,2)]
    return d


def defect(identity='9', quantity='4', kind='5705'):
    return dict(record_id=identity,workorder_id='OLD-MO',workorder_code='TEST-WO',xtransfer_no='CARD',route_id='1',process_id='2',responsible_process_id='1',material_item_code='RAW',unit_of_measure='PCS',quantity=quantity,type=kind,status='1',create_time='2024-01-02 11:00:00')


def run(d):
    orders,defects=convert(d)
    return convert_v2(d,orders,defects)


class ConversionV2Test(unittest.TestCase):
    def test_net396_plus_loss4_is_gross400_without_changing_employee(self):
        d=source(); d['source:pro_reporting_work'][1]['reporting_work_quantity']='396';d['source:qc_defective']=[defect()]
        ordinary,_,_,_=run(d);e=ordinary[0]['executions'][0]
        self.assertEqual([],e['issues']);self.assertEqual('400.000000',str(__import__('decimal').Decimal(e['nodes'][1]['gross']).quantize(__import__('decimal').Decimal('.000001'))))
        self.assertEqual('396',d['source:pro_reporting_work'][1]['reporting_work_quantity'])
        self.assertEqual('396',e['nodes'][1]['effective'])

    def test_multiple_reports_and_mixed_losses_are_aggregated_once(self):
        d=source();d['source:pro_reporting_work'][1]['reporting_work_quantity']='196'
        d['source:pro_reporting_work'].append(dict(d['source:pro_reporting_work'][1],record_id='3',user_id='13',reporting_work_quantity='200'))
        d['source:qc_defective']=[defect('9','3'),defect('10','1','5704')]
        e=run(d)[0][0]['executions'][0]
        self.assertEqual([],e['issues']);self.assertEqual('4',e['nodes'][1]['effectiveDefect'])
        self.assertEqual('NODE_AGGREGATE_ONLY_NO_EMPLOYEE_UPLIFT',e['nodes'][1]['allocation'])

    def test_late_defect_and_different_responsible_node_do_not_move_loss(self):
        d=source();d['source:pro_reporting_work'][1]['reporting_work_quantity']='396';d['source:qc_defective']=[defect()]
        nodes=run(d)[0][0]['executions'][0]['nodes']
        self.assertEqual('0',nodes[0]['effectiveDefect']);self.assertEqual('4',nodes[1]['effectiveDefect'])

    def test_scrapped_and_not_yet_scrapped_both_preserve_proven_local_loss(self):
        for status in ('0','1'):
            d=source();d['source:pro_reporting_work'][1]['reporting_work_quantity']='396';q=defect();q['status']=status;d['source:qc_defective']=[q]
            self.assertEqual([],run(d)[0][0]['executions'][0]['issues'])

    def test_void_unknown_unit_nitriding_and_component_material_fail_closed(self):
        for field,value in [('status','VOID'),('unit_of_measure','KG'),('type','5700'),('material_item_code','PART')]:
            d=source();q=defect();q[field]=value;d['source:qc_defective']=[q]
            self.assertTrue(run(d)[0][0]['executions'][0]['issues'])

    def test_modified_report_without_events_is_not_invented(self):
        d=source();d['source:pro_reporting_work'][0]['update_time']='2025-01-01'
        self.assertIn('REPORT_MODIFIED_WITHOUT_EVENT_HISTORY',run(d)[0][0]['executions'][0]['issues'])

    def test_negative_balance_and_unexplained_first_loss_are_rejected(self):
        d=source();d['source:pro_reporting_work_material'][0]['material']='[{"itemCode":"RAW","acceptedQty":-1,"qtyPer":1}]'
        self.assertIn('NEGATIVE_MATERIAL_BALANCE',run(d)[0][0]['executions'][0]['issues'])
        d=source();d['source:pro_reporting_work'][0]['reporting_work_quantity']='399'
        self.assertIn('NODE_QUANTITY_NOT_CONSERVED',run(d)[0][0]['executions'][0]['issues'])

    def test_broken_step_excludes_order_even_when_flag_is_off(self):
        d=source();d['source:pro_route_broken_step']=[dict(before_work_id='OLD-MO',before_lot_code='CARD',before_route_id='1',after_item_code='SEMI',after_lot_code='NEXT')]
        ordinary,forge,_,_=run(d)
        self.assertEqual([],ordinary);self.assertEqual(1,len(forge))


if __name__=='__main__':unittest.main()
