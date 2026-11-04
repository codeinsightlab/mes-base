-- Preserve existing platform counters in the empty factory scope. Real Factory IDs
-- are non-empty and receive independent durable counter rows.
ALTER TABLE sys_auto_code_result
  DROP INDEX uk_autocode_counter,
  ADD COLUMN factory_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Factory counter scope; empty is platform scope' AFTER rule_id,
  ADD UNIQUE KEY uk_autocode_counter (rule_id, factory_id, cycle_key);

-- Seed only the new local work-order number rule. Existing rule configuration is
-- retained as authoritative and is never overwritten by this migration.
INSERT INTO sys_auto_code_rule
  (rule_code, rule_name, rule_desc, max_length, is_padded, padded_char, padded_method,
   enable_flag, remark, attr1, create_by, create_time)
SELECT 'WORK_ORDER', 'WORK_ORDER编码规则', '本厂工单编号，规则编码作为getDocNo(type)参数',
       64, 'N', NULL, 'L', 'Y', '', 'V48_WORK_ORDER_SEED', 'migration', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_auto_code_rule WHERE rule_code='WORK_ORDER');

INSERT INTO sys_auto_code_part
  (rule_id, part_index, part_type, part_code, part_name, part_length, fix_character,
   create_by, create_time)
SELECT r.rule_id, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, 'WO', 'migration', CURRENT_TIMESTAMP
FROM sys_auto_code_rule r
WHERE r.rule_code='WORK_ORDER' AND r.attr1='V48_WORK_ORDER_SEED'
  AND NOT EXISTS (SELECT 1 FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id);

INSERT INTO sys_auto_code_part
  (rule_id, part_index, part_type, part_code, part_name, part_length, date_format,
   create_by, create_time)
SELECT r.rule_id, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', 'migration', CURRENT_TIMESTAMP
FROM sys_auto_code_rule r
WHERE r.rule_code='WORK_ORDER' AND r.attr1='V48_WORK_ORDER_SEED'
  AND (SELECT COUNT(*) FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id)=1;

INSERT INTO sys_auto_code_part
  (rule_id, part_index, part_type, part_code, part_name, part_length,
   seria_start_no, seria_step, cycle_flag, cycle_method, create_by, create_time)
SELECT r.rule_id, 3, 'SERIALNO', 'SERIAL', '流水号', 3,
       1, 1, 'Y', 'DAY', 'migration', CURRENT_TIMESTAMP
FROM sys_auto_code_rule r
WHERE r.rule_code='WORK_ORDER' AND r.attr1='V48_WORK_ORDER_SEED'
  AND (SELECT COUNT(*) FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id)=2;

UPDATE sys_auto_code_rule SET attr1=NULL WHERE rule_code='WORK_ORDER' AND attr1='V48_WORK_ORDER_SEED';
