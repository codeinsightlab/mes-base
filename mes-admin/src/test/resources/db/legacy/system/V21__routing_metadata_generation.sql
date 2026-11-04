-- 中心库路由配置与配置代次在同一事务中更新。
CREATE TABLE sys_routing_metadata (
    id INT NOT NULL PRIMARY KEY COMMENT '路由元数据状态单例编号，固定为1',
    generation BIGINT NOT NULL COMMENT '已提交的路由配置代次',
    delivered_generation BIGINT NOT NULL COMMENT '已持久确认完成的Redis缓存失效最大代次',
    last_error VARCHAR(128) NULL COMMENT '不含敏感信息的交付或确认错误摘要'
) COMMENT='路由元数据缓存失效恢复状态';
INSERT INTO sys_routing_metadata(id,generation,delivered_generation) VALUES(1,1,1);
