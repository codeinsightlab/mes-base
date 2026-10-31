-- 无工艺单阶段生产仍使用工单与执行；不制造虚构工艺，不修改既有工艺关联或生产事实。
ALTER TABLE workorder
    MODIFY COLUMN routing_id BIGINT NULL COMMENT '工单选定的适用已发布工艺，无工艺单阶段生产为空';

ALTER TABLE mes_production_execution
    MODIFY COLUMN routing_id BIGINT NULL COMMENT '执行冻结的工艺身份，无工艺单阶段生产为空';
