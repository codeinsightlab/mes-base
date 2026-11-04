-- 工厂管理保存鼎捷连接参数，不修改工厂运行模式及已有业务权威绑定。
ALTER TABLE mf_factory
    ADD COLUMN oapi_config_ref VARCHAR(128) NULL COMMENT '工厂鼎捷连接的不透明稳定引用';
ALTER TABLE mf_factory
    ADD COLUMN oapi_config_cipher TEXT NULL COMMENT '鼎捷地址及请求身份参数的加密配置';
ALTER TABLE mf_factory
    ADD COLUMN oapi_config_revision BIGINT NOT NULL DEFAULT 0 COMMENT '工厂鼎捷连接配置版本，未配置时为零';
