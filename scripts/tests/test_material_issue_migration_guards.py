"""Isolated SQLite fixtures evaluate migration SELECT guards and IF branches.
DDL and UPDATE are recorded only. This is not MySQL syntax, DDL, precision or Flyway acceptance.
No connections to MySQL, no filesystem databases and no persisted test tables.
"""
from pathlib import Path
import re,sqlite3,unittest
ROOT=Path(__file__).resolve().parents[2]
MIGRATIONS=ROOT/'mes-admin/src/main/resources/db/business'
class Rejected(Exception):pass
class GuardFixture:
    def __init__(self,old=False):
        self.db=sqlite3.connect(':memory:');self.db.create_function('DATABASE',0,lambda:'fixture')
        self.db.create_function('REGEXP_REPLACE',3,lambda s,p,r:re.sub(p,r,s))
        self.db.executescript('''ATTACH DATABASE ':memory:' AS information_schema;
CREATE TABLE information_schema.tables(table_schema,table_name,table_type);
CREATE TABLE information_schema.columns(table_schema,table_name,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name);
CREATE TABLE information_schema.statistics(table_schema,table_name,index_name,non_unique,seq_in_index,column_name,sub_part,collation,index_type,is_visible,expression);
CREATE TABLE information_schema.table_constraints(constraint_schema,table_name,constraint_name,constraint_type,enforced);
CREATE TABLE information_schema.check_constraints(constraint_schema,constraint_name,check_clause);
CREATE TABLE information_schema.key_column_usage(referenced_table_schema,referenced_table_name);
CREATE TABLE mes_material_issue_input(id,document_id,execution_id,input_kind,source_mode,output_qty,confirmed_at,stock_applied,stock_id,business_occurred_at,issue_id);
CREATE TABLE wm_stock_document(id,document_type,direction,status,create_request_id,source_execution_id,source_workorder_id,source_material_issue_id);
CREATE TABLE wm_stock_document_line(id,document_id,line_no,stock_action_id,material_id,warehouse_id,batch_no,unit_id,quantity,stock_id);
CREATE TABLE wm_stock_action(id,stock_id,action_type,document_line_id,reference_action_id,execution_id);
CREATE TABLE mes_production_execution(id,workorder_id);
INSERT INTO mes_production_execution VALUES(30,40);
INSERT INTO mes_material_issue_input VALUES('uuid',20,30,'PRODUCTION_INPUT','LOCAL_ONLY',2,'time',1,50,'time',10);
INSERT INTO wm_stock_document VALUES(20,'MATERIAL_ISSUE','OUT','POSTED','uuid',30,40,10);
INSERT INTO wm_stock_document_line VALUES(60,20,1,'uuid',70,80,'0001',90,5,50);
INSERT INTO wm_stock_action VALUES('uuid',50,'ISSUE',60,NULL,30);
INSERT INTO information_schema.table_constraints VALUES('fixture','wm_stock_document','ck_stock_document_direction','CHECK','YES');
INSERT INTO information_schema.check_constraints VALUES('fixture','ck_stock_document_direction',"(`direction` in (_utf8mb4'IN',_utf8mb4'OUT'))");
''')
        tables=['mes_material_issue_input','wm_stock_document','wm_stock_document_line','wm_stock_action','mes_production_execution']
        for t in tables:
            self.db.execute('INSERT INTO information_schema.tables VALUES(?,?,?)',('fixture',t,'BASE TABLE'))
            for _,c,*_ in self.db.execute(f'PRAGMA table_info({t})'):
                if c in ['issue_id','source_material_issue_id'] and not old:continue
                typ='bigint';nullable='YES';charset=None;collation=None
                if c=='direction':typ='varchar(3)';nullable='NO'
                if c=='create_request_id':typ='varchar(36)';charset='ascii';collation='ascii_bin'
                if c=='business_occurred_at':typ='datetime(6)'
                if c=='document_id' and t=='mes_material_issue_input' and not old:nullable='NO'
                self.db.execute('INSERT INTO information_schema.columns VALUES(?,?,?,?,?,?,?,?,?)',('fixture',t,c,typ,nullable,None,'',charset,collation))
        for t,n,cs,u in [('mes_material_issue_input','uk_input_document',['document_id'],0),('wm_stock_document','uk_stock_document_create_request',['create_request_id'],0),('wm_stock_document','idx_stock_document_direction_state',['direction','status','id'],1)]:
            for i,c in enumerate(cs,1):self.db.execute('INSERT INTO information_schema.statistics VALUES(?,?,?,?,?,?,?,?,?,?,?)',('fixture',t,n,u,i,c,None,'A','BTREE','YES',None))
        if old:
            self.db.executescript('''CREATE TABLE mes_material_issue(id,status,issue_time,workorder_id,execution_id);
CREATE TABLE mes_material_issue_item(issue_id,material_id,warehouse_id,batch_no,unit_id,quantity);
CREATE TABLE mes_material_issue_submission(issue_id,status);
CREATE TABLE mes_material_issue_erp_extra(id,source_execution_mode,external_effect,void_result_status);
INSERT INTO mes_material_issue VALUES(10,'CONFIRMED','time',40,30);
INSERT INTO mes_material_issue_item VALUES(10,70,80,'0001',90,5);
INSERT INTO mes_material_issue_submission VALUES(10,'SUCCEEDED');
INSERT INTO mes_material_issue_erp_extra VALUES(10,'LOCAL_ONLY','NONE',NULL);''')
            for t in ['mes_material_issue','mes_material_issue_item','mes_material_issue_submission','mes_material_issue_erp_extra']:
                self.db.execute('INSERT INTO information_schema.tables VALUES(?,?,?)',('fixture',t,'BASE TABLE'))
                for _,c,*_ in self.db.execute(f'PRAGMA table_info({t})'):
                    self.db.execute('INSERT INTO information_schema.columns VALUES(?,?,?,?,?,?,?,?,?)',('fixture',t,c,'fixture','YES',None,'',None,None))
        self.actions=[];self.vars={}
    def run(self,version):
        text=next(MIGRATIONS.glob(f'V{version}__*.sql')).read_text()
        text=re.sub(r'--[^\n]*','',text).split('BEGIN',1)[1].split('END$$',1)[0]
        chunks=re.split(r";(?=(?:[^']*'[^']*')*[^']*$)",text)
        stack=[];active=True
        def evaluate(expr):
            for k,v in self.vars.items():expr=re.sub(r'\b'+k+r'\b',str(v),expr)
            return bool(self.db.execute('SELECT '+expr).fetchone()[0])
        for chunk in chunks:
            s=chunk.strip()
            while s:
                m=re.match(r'IF\s+(.*?)\s+THEN\s*(.*)',s,re.S)
                if m:
                    cond=active and evaluate(m[1]);stack.append((active,cond));active=cond;s=m[2].strip();continue
                if s.startswith('ELSE'):
                    parent,cond=stack[-1];active=parent and not cond;s=s[4:].strip();continue
                if s.startswith('END IF'):
                    active=stack.pop()[0];s=s[6:].strip();continue
                break
            if not s or not active:continue
            if s.startswith('DECLARE'):
                self.vars[s.split()[1]]=0;continue
            if s.startswith('SIGNAL'):
                raise Rejected(re.search("MESSAGE_TEXT='([^']+)'",s)[1])
            if s.startswith(('ALTER','DROP','UPDATE')):
                self.actions.append(s);continue
            var=re.search(r'\bINTO\s+(\w+)',s)[1]
            query=re.sub(r'\bINTO\s+\w+\s*','',s)
            if isinstance(self.db,sqlite3.Connection):query=query.replace('<=>',' IS ')
            self.vars[var]=self.db.execute(query).fetchone()[0]
        assert not stack
        return self.actions
class MaterialIssueMigrationGuards(unittest.TestCase):
    def test_v74_matching_objects_do_nothing(self):
        self.assertEqual([],GuardFixture().run(74))
    def test_v74_adds_only_absent_field_and_index(self):
        f=GuardFixture();f.db.execute("DELETE FROM information_schema.columns WHERE column_name='create_request_id'")
        f.db.execute("DELETE FROM information_schema.statistics WHERE index_name='uk_stock_document_create_request'")
        actions=f.run(74);self.assertEqual(2,len(actions));self.assertTrue(all('create_request' in x for x in actions))
    def test_v74_new_direction_maps_once(self):
        f=GuardFixture();f.db.execute("DELETE FROM information_schema.columns WHERE column_name='direction'")
        actions=f.run(74);self.assertEqual(1,sum(x.startswith('UPDATE') for x in actions))
    def test_v74_existing_semantic_conflict_rejects_before_ddl(self):
        f=GuardFixture();f.db.execute("UPDATE wm_stock_document SET direction='IN'")
        with self.assertRaisesRegex(Rejected,'direction conflicts'):f.run(74)
        self.assertEqual([],f.actions)
    def test_v75_all_old_present_and_converted_plans_retirement(self):
        f=GuardFixture(True);actions=f.run(75)
        self.assertEqual(3,len(actions));self.assertEqual(1,sum(x.startswith('DROP TABLE') for x in actions))
    def test_v75_old_normally_absent_and_target_complete_skips(self):
        self.assertEqual([],GuardFixture().run(75))
    def test_v75_unbound_draft_without_input_remains_legal(self):
        f=GuardFixture();f.db.execute("INSERT INTO wm_stock_document VALUES(21,'MATERIAL_ISSUE','OUT','DRAFT',NULL,NULL,40,NULL)")
        self.assertEqual([],f.run(75))
    def test_v75_missing_targets_fail_instead_of_count_zero_success(self):
        f=GuardFixture();f.db.execute("DELETE FROM information_schema.tables WHERE table_name='mes_material_issue_input'")
        with self.assertRaisesRegex(Rejected,'target tables'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_conflicting_existing_column_rejects(self):
        f=GuardFixture();f.db.execute("UPDATE information_schema.columns SET column_type='varchar(99)' WHERE column_name='document_id'")
        with self.assertRaisesRegex(Rejected,'target document_id'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_index_with_extra_fourth_column_rejects(self):
        f=GuardFixture();f.db.execute("INSERT INTO information_schema.statistics SELECT table_schema,table_name,index_name,non_unique,4,'document_type',sub_part,collation,index_type,is_visible,expression FROM information_schema.statistics WHERE index_name='idx_stock_document_direction_state' AND seq_in_index=1")
        with self.assertRaisesRegex(Rejected,'Conflicting idx_stock'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_nonunique_input_index_rejects(self):
        f=GuardFixture();f.db.execute("UPDATE information_schema.statistics SET non_unique=1 WHERE index_name='uk_input_document'")
        with self.assertRaisesRegex(Rejected,'Conflicting uk_input'):f.run(75)
    def test_v75_unconverted_old_input_rejects_before_drop(self):
        f=GuardFixture(True);f.db.execute('UPDATE mes_material_issue_input SET document_id=NULL')
        with self.assertRaisesRegex(Rejected,'formal input identity'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_unconverted_old_line_quantity_rejects(self):
        f=GuardFixture(True);f.db.execute('UPDATE mes_material_issue_item SET quantity=9')
        with self.assertRaisesRegex(Rejected,'Unconverted material issue line'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_partial_old_tables_rejects(self):
        f=GuardFixture(True);f.db.execute("DELETE FROM information_schema.tables WHERE table_name='mes_material_issue_submission'")
        with self.assertRaisesRegex(Rejected,'Partial old issue'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_missing_original_action_rejects_even_with_old_absent(self):
        f=GuardFixture();f.db.execute('DELETE FROM wm_stock_action')
        with self.assertRaisesRegex(Rejected,'original inventory action'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_unresolved_submission_rejects(self):
        f=GuardFixture(True);f.db.execute("UPDATE mes_material_issue_submission SET status='UNKNOWN'")
        with self.assertRaisesRegex(Rejected,'Unresolved original submission'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_empty_sources_do_not_hide_remaining_target_orphan(self):
        f=GuardFixture(True);f.db.execute('DELETE FROM mes_material_issue_input')
        for t in ['mes_material_issue','mes_material_issue_item','mes_material_issue_submission','mes_material_issue_erp_extra']:f.db.execute('DELETE FROM '+t)
        with self.assertRaisesRegex(Rejected,'missing its formal input'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_orphan_old_item_fails_before_retirement(self):
        f=GuardFixture(True);f.db.execute("INSERT INTO mes_material_issue_item VALUES(999,70,80,'0001',90,5)")
        with self.assertRaisesRegex(Rejected,'orphan in mes_material_issue_item'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_orphan_old_submission_fails_before_retirement(self):
        f=GuardFixture(True);f.db.execute("INSERT INTO mes_material_issue_submission VALUES(999,'SUCCEEDED')")
        with self.assertRaisesRegex(Rejected,'orphan in mes_material_issue_submission'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_orphan_old_extra_fails_before_retirement(self):
        f=GuardFixture(True);f.db.execute("INSERT INTO mes_material_issue_erp_extra VALUES(999,'LOCAL_ONLY','NONE',NULL)")
        with self.assertRaisesRegex(Rejected,'orphan in mes_material_issue_erp_extra'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_orphan_old_input_fails_before_retirement(self):
        f=GuardFixture(True);f.db.execute("UPDATE mes_material_issue_input SET issue_id=999")
        with self.assertRaisesRegex(Rejected,'orphan in mes_material_issue_input'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_old_execution_differs_from_formal_input_rejects(self):
        f=GuardFixture(True);f.db.execute('UPDATE mes_material_issue SET execution_id=999')
        with self.assertRaisesRegex(Rejected,'Unconverted production input reference'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_old_workorder_differs_from_formal_execution_rejects(self):
        f=GuardFixture(True);f.db.execute('UPDATE mes_material_issue SET workorder_id=999')
        with self.assertRaisesRegex(Rejected,'Unconverted production input reference'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_partially_missing_source_mapping_column_rejects(self):
        f=GuardFixture(True);f.db.execute("DELETE FROM information_schema.columns WHERE table_name='mes_material_issue_input' AND column_name='issue_id'")
        with self.assertRaisesRegex(Rejected,'Old source mapping is incomplete'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_partially_missing_source_column_rejects(self):
        f=GuardFixture(True);f.db.execute("DELETE FROM information_schema.columns WHERE table_name='mes_material_issue' AND column_name='execution_id'")
        with self.assertRaisesRegex(Rejected,'Old source columns are incomplete'):f.run(75)
        self.assertEqual([],f.actions)
    def test_v75_check_not_enforced_rejects(self):
        f=GuardFixture();f.db.execute("UPDATE information_schema.table_constraints SET enforced='NO'")
        with self.assertRaisesRegex(Rejected,'Conflicting ck_stock'):f.run(75)
        self.assertEqual([],f.actions)
if __name__=='__main__':unittest.main(verbosity=2)
