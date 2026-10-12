-- 仅供隔离真实数据副本验证；运行约定和前置检查见同目录 README.md。
-- 保留来源流水编码及缺陷类型、等级；不带入扣款规则。
START TRANSACTION;
INSERT INTO qc_defect (
    defect_id, factory_id, defect_code, defect_name, index_type, defect_level,
    remark, create_by, create_time, update_by, update_time
)
SELECT
    s.defect_id, @target_factory_id, s.defect_code, s.defect_name, s.index_type, s.defect_level,
    s.remark, s.create_by, s.create_time, s.update_by, s.update_time
FROM ktg_mes_isolated.qc_defect s;
COMMIT;
