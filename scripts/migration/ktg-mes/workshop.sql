-- 仅供隔离真实数据副本验证；运行约定和前置检查见同目录 README.md。
-- 保留来源主键；面积转定点数；负责人保持文本。
-- 不复制 ERP 部门、员工、仓库绑定及隐藏仓库字段 attr1。
-- 旧部门编号未经中心目录映射核实，不直接复制；目标 dept_id 暂为空。
START TRANSACTION;
INSERT INTO md_workshop (
    workshop_id, factory_id, workshop_code, workshop_name, area, charge,
    dept_id, enable_flag, remark, create_by, create_time, update_by, update_time
)
SELECT
    s.workshop_id, @target_factory_id, s.workshop_code, s.workshop_name,
    CAST(s.area AS DECIMAL(18,2)), s.charge, NULL, s.enable_flag, s.remark,
    s.create_by, s.create_time, s.update_by, s.update_time
FROM ktg_mes_isolated.md_workshop s;
COMMIT;
