"""Synthetic admission/frozen-scope regressions; no database access."""
import copy
import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]/'legacy'))
from report_driven import convert,canonical,digest
from conversion_v2 import convert_v2
from prepare_component import build_command
from review_v21 import audit_component
from scripts.tests.test_legacy_conversion_v2 import source


def fixture(partial=False):
    data=source()
    data['source:pro_reporting_work'].append(dict(data['source:pro_reporting_work'][0],record_id='3',xtransfer_no='CARD2'))
    if not partial:data['source:pro_reporting_work'].append(dict(data['source:pro_reporting_work'][1],record_id='4',xtransfer_no='CARD2'))
    data['source:pro_material'].append(dict(data['source:pro_material'][0],record_id='2',xtransfer_no='CARD2',issue_doc_no='ISSUE2'))
    data['source:pro_reporting_work_material'] += [dict(r,xtransfer_no='CARD2',material='[{"itemCode":"RAW","acceptedQty":'+('400' if partial and r['process_id']=='2' else '0')+',"qtyPer":1}]') for r in data['source:pro_reporting_work_material']]
    w=convert_v2(data,*convert(data))[0][0]
    account={'user_id':'12','user_name':'TEST_USER','nick_name':'测试身份','create_time':'2023-01-01 00:00:00'}
    ref={'md_item':[{'item_code':'PRODUCT','item_name':'测试成品','unit_of_measure':'PCS','specification':None}],
         'pro_process':[{'process_id':str(i),'process_code':'P'+str(i),'process_name':'工序'+str(i)} for i in (1,2)],'sys_user':[account]}
    units=[{'code':'PCS','name':'件'}]
    target={'sys_user':[copy.deepcopy(account)],'material':[],'unit_measure':[],'operation':[],'workorder':[],'mes_legacy_source_mapping':[]}
    return w,ref,target,units


class AdmissionTest(unittest.TestCase):
    def test_card_subset_keeps_full_order_snapshot_and_plan(self):
        w,ref,target,units=fixture();key=canonical(w['workorder']['executions'][1]['key'])
        c=build_command(w,ref,{'12':target['sys_user'][0]},units,'a'*64,'TEST_BATCH','TEST_FACTORY',[key])
        self.assertEqual(1,len(c['executions']));self.assertEqual('800',c['plannedQty']);self.assertEqual(digest(w['workorder']),c['sourceHash'])
        self.assertEqual(2,len(c['executions'][0]['reports']))

    def test_duplicate_login_is_not_identity_proof(self):
        w,ref,target,units=fixture();target['sys_user'].append(dict(target['sys_user'][0],user_id='99'))
        with self.assertRaisesRegex(ValueError,'NOT_UNIQUE'):build_command(w,ref,{r['user_id']:r for r in target['sys_user']},units,'a'*64,'TEST','TEST')

    def test_name_or_creation_change_requires_review(self):
        for field,value in [('nick_name','其他人'),('create_time','2025-01-01 00:00:00')]:
            w,ref,target,units=fixture();target['sys_user'][0][field]=value
            self.assertEqual('NEEDS_REVIEW',audit_component(w,ref,target,units,'a'*64)[0])

    def test_historical_login_conflict_is_not_hidden_by_current_account_match(self):
        w,ref,target,units=fixture()
        w['workorder']['executions'][0]['reports'][0]['raw']['user_name']='DIFFERENT_HISTORICAL_LOGIN'
        status,reasons,_,_=audit_component(w,ref,target,units,'a'*64)
        self.assertEqual('NEEDS_REVIEW',status)
        self.assertTrue(any('HISTORICAL_REPORT_LOGIN_REQUIRES_REVIEW' in reason for reason in reasons))

    def test_matching_recreated_account_cannot_own_older_report(self):
        w,ref,target,units=fixture()
        ref['sys_user'][0]['create_time']='2025-01-01 00:00:00'
        target['sys_user'][0]['create_time']='2025-01-01 00:00:00'
        status,reasons,_,_=audit_component(w,ref,target,units,'a'*64)
        self.assertEqual('NEEDS_REVIEW',status)
        self.assertTrue(any('ACCOUNT_CREATED_AFTER_REPORT' in reason for reason in reasons))

    def test_modified_history_is_rejected_not_approved(self):
        w,ref,target,units=fixture();w['executions'][0]['issues'].append('REPORT_MODIFIED_WITHOUT_EVENT_HISTORY')
        self.assertEqual('REJECTED',audit_component(w,ref,target,units,'a'*64)[0])

    def test_partial_card_cannot_silently_change_shared_route(self):
        w,ref,target,units=fixture(True);status,reasons,_,_=audit_component(w,ref,target,units,'a'*64)
        self.assertEqual('NEEDS_REVIEW',status);self.assertIn('ACTUAL_PATH_SET_MISMATCH',reasons)

    def test_target_code_collision_requires_semantic_approval(self):
        w,ref,target,units=fixture();target['material']=[{'id':'77','code':'PRODUCT'}]
        status,reasons,_,_=audit_component(w,ref,target,units,'a'*64)
        self.assertEqual('NEEDS_REVIEW',status);self.assertIn('TARGET_CODE_REQUIRES_SEMANTIC_APPROVAL:material',reasons)


if __name__=='__main__':unittest.main()
