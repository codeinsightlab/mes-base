-- 仅供隔离真实数据副本验证；运行约定和前置检查见同目录 README.md。
-- 保留来源项目类型原始值，包括尚待字典核对的历史值，不推测新状态。
START TRANSACTION;
INSERT INTO dv_subject (
    subject_id, factory_id, subject_code, subject_name, subject_type,
    subject_content, subject_standard, enable_flag, remark,
    create_by, create_time, update_by, update_time
)
SELECT
    s.subject_id, @target_factory_id, s.subject_code, s.subject_name, s.subject_type,
    s.subject_content, s.subject_standard, s.enable_flag, s.remark,
    s.create_by, s.create_time, s.update_by, s.update_time
FROM ktg_mes_isolated.dv_subject s;
COMMIT;
