-- 当前单节点申报只保留实际生效的affected_node_id。
-- V48.1已由并发迁移入口应用，原文件按已执行校验和保留；后续清理必须向前迁移。
ALTER TABLE mes_production_defect DROP COLUMN discovered_node_id;
ALTER TABLE mes_production_defect DROP COLUMN responsible_node_id;
