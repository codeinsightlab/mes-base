-- V9 迁移并完成图链路验证后退役旧步骤表，避免保留两套正式事实。
-- 校验防止 V9 之后旧服务继续写步骤造成静默丢失；有差异明确失败并人工核对。
DELIMITER $$
DROP PROCEDURE IF EXISTS verify_legacy_routing_step_migrated$$
CREATE PROCEDURE verify_legacy_routing_step_migrated()
BEGIN
    IF EXISTS (
        SELECT 1 FROM routing_step s LEFT JOIN routing_node n ON n.id=s.id
        WHERE n.id IS NULL OR NOT (
            n.routing_id <=> s.routing_id AND n.operation_id <=> s.operation_id
            AND n.operation_code <=> s.operation_code AND n.operation_name <=> s.operation_name
            AND n.operation_description <=> s.operation_description AND n.is_key <=> s.is_key
            AND n.description <=> s.description AND n.display_order <=> s.sequence
        )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='旧步骤与迁移节点不一致，须人工核对后再退役';
    END IF;
END$$
DELIMITER ;
CALL verify_legacy_routing_step_migrated();
DROP PROCEDURE verify_legacy_routing_step_migrated;
DROP TABLE routing_step;
