ALTER TABLE unit_measure
    ADD COLUMN must_be_whole_number TINYINT(1) NOT NULL DEFAULT 0 COMMENT '数量必须为整数：1是、0否',
    ADD COLUMN quantity_precision TINYINT UNSIGNED NOT NULL DEFAULT 6 COMMENT '该单位业务数量允许的小数位数，范围0至6';
