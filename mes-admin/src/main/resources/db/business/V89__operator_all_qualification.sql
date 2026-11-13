-- 用户工序资格仅在当前工厂业务库生效，不改变任何指定工序关系。
CREATE TABLE mes_operator_qualification (
    user_id BIGINT NOT NULL COMMENT '中心系统用户主键，本厂用户资格设置唯一身份',
    all_operations BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否拥有本厂全部适用且启用工序的操作资格',
    revision INT NOT NULL COMMENT '配置并发版本，由服务端递增',
    create_by BIGINT NOT NULL COMMENT '首次创建配置的可信系统用户主键',
    create_time DATETIME(6) NOT NULL COMMENT '首次创建配置的服务端时间',
    update_by BIGINT NOT NULL COMMENT '最近修改配置的可信系统用户主键',
    update_time DATETIME(6) NOT NULL COMMENT '最近修改配置的服务端时间',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当前工厂用户全工序资格设置，指定工序关系独立保留';
