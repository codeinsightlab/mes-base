-- 仅供隔离真实数据副本验证；运行约定和前置检查见同目录 README.md。
-- 保留检测类型原始字典值，不导入客户字典数据或检验流程记录。
START TRANSACTION;
INSERT INTO qc_index (
    index_id, factory_id, index_code, index_name, index_type, qc_tool,
    remark, create_by, create_time, update_by, update_time
)
SELECT
    s.index_id, @target_factory_id, s.index_code, s.index_name, s.index_type, s.qc_tool,
    s.remark, s.create_by, s.create_time, s.update_by, s.update_time
FROM ktg_mes_isolated.qc_index s;
COMMIT;
