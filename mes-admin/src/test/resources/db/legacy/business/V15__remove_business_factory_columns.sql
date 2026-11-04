-- 按工厂独立业务库移除冗余工厂列，保留既有索引与唯一性约束的业务列。

-- 不修改中心库工厂配置、授权和同步协调键；执行前必须备份并检查库内混厂及重复键。

ALTER TABLE dv_subject
    DROP INDEX `idx_subject_factory_code`,
    ADD KEY `idx_subject_code` (`subject_code`),
    DROP INDEX `idx_subject_factory_enabled`,
    ADD KEY `idx_subject_enabled` (`enable_flag`),
    DROP INDEX `idx_subject_factory_type`,
    ADD KEY `idx_subject_type` (`subject_type`),
    DROP COLUMN factory_id;

ALTER TABLE equipment
    DROP INDEX `uk_equipment_factory_code`,
    ADD UNIQUE KEY `uk_equipment_code` (`code`),
    DROP INDEX `idx_equipment_workstation`,
    ADD KEY `idx_equipment_workstation` (`workstation_id`),
    DROP COLUMN factory_id;

ALTER TABLE material
    DROP INDEX `uk_material_factory_key`,
    ADD UNIQUE KEY `uk_material_key` (`code`),
    DROP INDEX `idx_material_category`,
    ADD KEY `idx_material_category` (`category_id`),
    DROP INDEX `idx_material_unit`,
    ADD KEY `idx_material_unit` (`unit_id`),
    DROP COLUMN factory_id;

ALTER TABLE material_category
    DROP INDEX `uk_material_category_factory_key`,
    ADD UNIQUE KEY `uk_material_category_key` (`code`),
    DROP INDEX `idx_category_parent`,
    ADD KEY `idx_category_parent` (`parent_id`),
    DROP COLUMN factory_id;

ALTER TABLE md_unit_measure
    DROP INDEX `idx_unit_factory_code`,
    ADD KEY `idx_unit_code` (`measure_code`),
    DROP INDEX `idx_unit_factory_enabled`,
    ADD KEY `idx_unit_enabled` (`enable_flag`),
    DROP INDEX `idx_unit_factory_primary`,
    ADD KEY `idx_unit_primary` (`primary_id`),
    DROP COLUMN factory_id;

ALTER TABLE md_workshop
    DROP INDEX `idx_workshop_factory_code`,
    ADD KEY `idx_workshop_code` (`workshop_code`),
    DROP INDEX `idx_workshop_factory_enabled`,
    ADD KEY `idx_workshop_enabled` (`enable_flag`),
    DROP INDEX `idx_workshop_factory_dept`,
    ADD KEY `idx_workshop_dept` (`dept_id`),
    DROP COLUMN factory_id;

ALTER TABLE mes_material_issue
    DROP INDEX `idx_issue_no`,
    ADD KEY `idx_issue_no` (`issue_no`),
    DROP INDEX `idx_issue_workorder`,
    ADD KEY `idx_issue_workorder` (`workorder_id`),
    DROP INDEX `idx_issue_workorder_code`,
    ADD KEY `idx_issue_workorder_code` (`workorder_code`),
    DROP INDEX `idx_issue_routing`,
    ADD KEY `idx_issue_routing` (`routing_id`),
    DROP INDEX `idx_issue_flow_card`,
    ADD KEY `idx_issue_flow_card` (`flow_card_no`),
    DROP COLUMN factory_id;

ALTER TABLE mes_material_issue_item
    DROP INDEX `idx_issue_item_parent`,
    ADD KEY `idx_issue_item_parent` (`issue_id`),
    DROP INDEX `idx_issue_item_material_batch`,
    ADD KEY `idx_issue_item_material_batch` (`material_code`, `batch_no`),
    DROP INDEX `idx_issue_item_material`,
    ADD KEY `idx_issue_item_material` (`material_id`),
    DROP INDEX `idx_issue_item_warehouse`,
    ADD KEY `idx_issue_item_warehouse` (`warehouse_id`),
    DROP COLUMN factory_id;

ALTER TABLE operation
    DROP INDEX `uk_operation_factory_code`,
    ADD UNIQUE KEY `uk_operation_code` (`code`),
    DROP COLUMN factory_id;

ALTER TABLE routing
    DROP INDEX `uk_routing_factory_version`,
    ADD UNIQUE KEY `uk_routing_version` (`code`, `version`),
    DROP COLUMN factory_id;

ALTER TABLE safety_stock
    DROP INDEX `uk_safety_stock_factory_key`,
    ADD UNIQUE KEY `uk_safety_stock_key` (`material_id`),
    DROP COLUMN factory_id;

ALTER TABLE unit_measure
    DROP INDEX `uk_unit_measure_factory_key`,
    ADD UNIQUE KEY `uk_unit_measure_key` (`code`),
    DROP COLUMN factory_id;

ALTER TABLE wm_inventory
    DROP INDEX `uk_inventory_factory_material_warehouse`,
    ADD UNIQUE KEY `uk_inventory_material_warehouse` (`material_id`, `warehouse_id`),
    DROP INDEX `idx_inventory_warehouse`,
    ADD KEY `idx_inventory_warehouse` (`warehouse_id`),
    DROP COLUMN factory_id;

ALTER TABLE wm_warehouse
    DROP INDEX `uk_warehouse_factory_code`,
    ADD UNIQUE KEY `uk_warehouse_code` (`code`),
    DROP COLUMN factory_id;

ALTER TABLE workshop
    DROP INDEX `uk_workshop_factory_key`,
    ADD UNIQUE KEY `uk_workshop_key` (`code`),
    DROP COLUMN factory_id;

ALTER TABLE workstation
    DROP INDEX `uk_workstation_factory_key`,
    ADD UNIQUE KEY `uk_workstation_key` (`code`),
    DROP INDEX `idx_station_workshop`,
    ADD KEY `idx_station_workshop` (`workshop_id`),
    DROP COLUMN factory_id;
