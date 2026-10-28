ALTER TABLE sys_auto_code_rule
    ADD COLUMN rule_type VARCHAR(32) NULL COMMENT '业务单据编码类型' AFTER rule_code;

-- 仅给已有的系统工单规则分类；其他历史规则保持未分类，避免猜测业务用途。
UPDATE sys_auto_code_rule SET rule_type='WORK_ORDER' WHERE rule_code='WORK_ORDER';

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
SELECT '单据编码类型','sys_autocode_ruletype','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='sys_autocode_ruletype');

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 1,'工单','WORK_ORDER','sys_autocode_ruletype','N','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='sys_autocode_ruletype' AND dict_value='WORK_ORDER');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 2,'出库单','STOCK_OUT','sys_autocode_ruletype','N','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='sys_autocode_ruletype' AND dict_value='STOCK_OUT');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 3,'入库单','STOCK_IN','sys_autocode_ruletype','N','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='sys_autocode_ruletype' AND dict_value='STOCK_IN');
