-- 路线拓扑增量升级；保留已执行的 V3/V4 校验和以及每个路线版本的冻结快照。
-- 节点编码唯一和边唯一约束按本轮用户明确要求建立；不新增物理外键。
CREATE TABLE routing_node (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '路线工艺节点本地主键',
    routing_id BIGINT NOT NULL COMMENT '所属路线确切版本主键，工厂范围继承父级',
    node_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '版本内稳定节点编码，同工序可对应多个节点',
    operation_id BIGINT NOT NULL COMMENT '节点引用的可复用工序定义主键',
    operation_code VARCHAR(64) NULL COMMENT '发布时冻结的工序编码',
    operation_name VARCHAR(255) NULL COMMENT '发布时冻结的工序名称',
    operation_description VARCHAR(1000) NULL COMMENT '发布时冻结的工艺要求，历史未知值保持为空',
    is_key BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否关键工艺节点，沿用版本最多一个关键点',
    description VARCHAR(1000) NULL COMMENT '此工艺节点的补充说明',
    display_order INTEGER NOT NULL DEFAULT 0 COMMENT '显示排序提示，不决定执行拓扑',
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_node_code (routing_id,node_code),
    KEY idx_routing_node_operation (operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本工艺节点，执行关系仅由连接边表达';

CREATE TABLE routing_edge (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '路线节点连接边本地主键',
    routing_id BIGINT NOT NULL COMMENT '所属路线确切版本主键，工厂范围继承父级',
    from_node_id BIGINT NOT NULL COMMENT '连接起点工艺节点主键，必须属于同一版本',
    to_node_id BIGINT NOT NULL COMMENT '连接终点工艺节点主键，必须属于同一版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_edge (routing_id,from_node_id,to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本有向连接边，唯一执行拓扑事实';

-- 保留旧步骤主键及所有快照，不读取今天的工序覆盖历史发布内容。
INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,
                         operation_description,is_key,description,display_order)
SELECT id,routing_id,CONCAT('N',sequence),operation_id,operation_code,operation_name,
       operation_description,is_key,description,sequence
FROM routing_step;

-- 每条旧线性路线相邻步骤转换成一条边，排序字段此后只作为展示提示。
INSERT INTO routing_edge(routing_id,from_node_id,to_node_id)
SELECT routing_id,previous_id,id
FROM (SELECT routing_id,id,LAG(id) OVER (PARTITION BY routing_id ORDER BY sequence,id) previous_id
      FROM routing_step) ordered_steps
WHERE previous_id IS NOT NULL;
