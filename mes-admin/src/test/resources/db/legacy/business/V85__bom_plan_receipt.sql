CREATE TABLE material_bom_plan_receipt (
 request_id CHAR(36) PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 plan_hash CHAR(64) NOT NULL,
 root_id BIGINT NULL
) COMMENT='整阶BOM确认回执';
