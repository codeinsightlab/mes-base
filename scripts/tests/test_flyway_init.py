"""Safety guards for the V1 distribution. No database connection or SQL execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / 'mes-admin/src/main/resources/db'


class FlywayInitDistributionTest(unittest.TestCase):
    def test_only_current_chains_are_distributed(self):
        self.assertEqual({'center/V1__init.sql', 'business/V1__init.sql'},
                         {str(p.relative_to(DB)) for p in DB.rglob('*.sql')})
        java = ROOT / 'mes-admin/src/main/java/dev/mesbase/server/schema'
        self.assertEqual([], list(java.glob('V*__*.java')))

    def test_init_does_not_destroy_or_select_an_environment(self):
        for p in DB.rglob('*.sql'):
            sql = re.sub(r'--[^\n]*', '', p.read_text())
            self.assertIsNone(re.search(r'^\s*(DROP|TRUNCATE|DELETE|REPLACE|USE)\b', sql, re.I | re.M), p)
            self.assertIsNone(re.search(r'\bCREATE\s+(DATABASE|SCHEMA|USER)\b', sql, re.I), p)
            self.assertNotRegex(sql, r'(?i)AUTO_INCREMENT\s*=\s*\d+')
            self.assertIn('SET FOREIGN_KEY_CHECKS = @mes_init_foreign_key_checks;', sql)

    def test_seeds_are_product_catalog_only(self):
        center = (DB / 'center/V1__init.sql').read_text()
        allowed = {'sys_menu', 'sys_dict_type', 'sys_dict_data', 'sys_auto_code_rule',
                   'sys_auto_code_part', 'platform_catalog_revision', 'sys_routing_metadata'}
        targets = set(re.findall(r'(?i)INSERT\s+INTO\s+`?(\w+)', center))
        self.assertEqual(allowed, targets)
        self.assertNotRegex((DB / 'business/V1__init.sql').read_text(), r'(?i)\bINSERT\s+INTO\b')
        for table in ('sys_auto_code_rule', 'sys_auto_code_part'):
            columns = re.search(r'INSERT INTO `' + table + r'` \(([^)]+)\)', center).group(1)
            self.assertNotIn('factory_id', columns)
            self.assertNotIn('seria_now_no', columns)
            self.assertNotIn('create_by', columns)

    def test_seed_references_do_not_require_customer_records(self):
        sql = (DB / 'center/V1__init.sql').read_text()
        menus = re.search(r'INSERT INTO `sys_menu` .*? VALUES\n(.*?);', sql, re.S).group(1)
        # SQL strings may contain commas; the leading id/name/parent tuple has a stable boundary.
        rows = re.findall(r"^\((\d+),'(?:''|\\.|[^'])*',(\d+),", menus, re.M)
        self.assertTrue(rows)
        ids = {int(row[0]) for row in rows}
        self.assertEqual(len(rows), len(ids))
        self.assertTrue(all(int(parent) == 0 or int(parent) in ids for _, parent in rows))


if __name__ == '__main__':
    unittest.main(verbosity=2)
