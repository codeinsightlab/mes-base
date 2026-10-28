ALTER TABLE sys_auto_code_rule
    ADD COLUMN factory_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
        COMMENT '适用工厂，空值表示全局规则' AFTER rule_type,
    ADD KEY idx_autocode_rule_factory_type (factory_id, rule_type),
    ADD CONSTRAINT fk_autocode_rule_factory
        FOREIGN KEY (factory_id) REFERENCES mf_factory (factory_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
