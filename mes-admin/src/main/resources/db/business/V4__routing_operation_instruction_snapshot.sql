-- 发布版本同时冻结工序定义中的工艺要求，避免后续编辑工序使历史执行依据漂移。
ALTER TABLE routing_step
    ADD COLUMN operation_description VARCHAR(1000) NULL
    COMMENT '发布时冻结的工序工艺要求，草稿可为空'
    AFTER operation_name;
