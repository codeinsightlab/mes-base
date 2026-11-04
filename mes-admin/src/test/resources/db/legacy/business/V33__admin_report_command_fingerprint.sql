-- 管理员补录、订正和冲销复用现有请求唯一键，历史流水保持不变。
ALTER TABLE mes_production_report
 ADD COLUMN command_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '管理员报工命令指纹，包含动作及可信操作者，历史和正式报工为空';
