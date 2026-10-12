-- 仅供隔离真实数据副本验证；运行约定和前置检查见同目录 README.md。
-- ktg_mes_isolated 必须是事先确认的单工厂隔离副本，不得替换为生产库。
-- 执行者必须在当前会话设置已核实的 @target_factory_id；不提供默认工厂。
-- 保留来源主键和主单位引用；换算率由浮点数显式转换为十进制定点数。
START TRANSACTION;
INSERT INTO md_unit_measure (
    measure_id, factory_id, measure_code, measure_name, primary_flag,
    primary_id, change_rate, enable_flag, remark,
    create_by, create_time, update_by, update_time
)
SELECT
    s.measure_id, @target_factory_id, s.measure_code, s.measure_name, s.primary_flag,
    s.primary_id, CAST(s.change_rate AS DECIMAL(18,6)), s.enable_flag, s.remark,
    s.create_by, s.create_time, s.update_by, s.update_time
FROM ktg_mes_isolated.md_unit_measure s;
COMMIT;
