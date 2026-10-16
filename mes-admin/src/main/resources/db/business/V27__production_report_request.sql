-- 正式报工请求标识与原始流水同事务保存；管理员补录和订正保持空值。
-- 唯一范围为当前独立业务库，允许多条空标识的历史流水。
ALTER TABLE mes_production_report
 ADD COLUMN request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '正式生产报工的客户端请求UUID，历史补录和订正为空',
 ADD UNIQUE KEY uk_report_request_id(request_id);
