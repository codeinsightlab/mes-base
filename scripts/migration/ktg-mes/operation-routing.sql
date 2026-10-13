-- Operation 与 Routing 最小离线转换器。仅执行于已授权的隔离业务库。
-- 将 source_operation_routing 替换为冻结的真实 Source 副本库名，并先设置 @target_factory。
-- 前置条件：适用的 Source 物料已转换到本厂 material，引用按编码映射本地主键。
-- 首次导入专用，重复导入由唯一约束拒绝；不使用 IGNORE 或默认引用掩盖坏数据。
CREATE TEMPORARY TABLE conversion_reject (
    entity_type VARCHAR(32) NOT NULL COMMENT '拒绝对象类型',
    source_id BIGINT NOT NULL COMMENT '被拒绝的来源记录主键',
    reason VARCHAR(128) NOT NULL COMMENT '拒绝原因代码',
    KEY idx_conversion_reject (entity_type,source_id)
) COMMENT='本次转换拒绝清单，不保存公司业务明细';

INSERT INTO conversion_reject
SELECT 'OPERATION',p.process_id,'INVALID_OPERATION_FIELDS'
FROM source_operation_routing.pro_process p
WHERE NULLIF(TRIM(p.process_code),'') IS NULL OR NULLIF(TRIM(p.process_name),'') IS NULL
   OR COALESCE(p.enable_flag,'') NOT IN ('Y','N') OR CHAR_LENGTH(p.process_code)>64 OR CHAR_LENGTH(p.process_name)>255;
INSERT INTO conversion_reject
SELECT 'OPERATION',p.process_id,'DUPLICATE_OPERATION_CODE'
FROM source_operation_routing.pro_process p
JOIN (SELECT TRIM(process_code) code FROM source_operation_routing.pro_process
      GROUP BY TRIM(process_code) HAVING COUNT(*)>1) d ON TRIM(p.process_code)=d.code;

INSERT INTO conversion_reject
SELECT 'STEP',s.record_id,'MISSING_PARENT_OR_OPERATION'
FROM source_operation_routing.pro_route_process s
LEFT JOIN source_operation_routing.pro_route r ON r.route_id=s.route_id
LEFT JOIN source_operation_routing.pro_process o ON o.process_id=s.process_id
WHERE r.route_id IS NULL OR o.process_id IS NULL;

INSERT INTO conversion_reject
SELECT 'ROUTING',r.route_id,'INVALID_ROUTING_FIELDS'
FROM source_operation_routing.pro_route r
WHERE NULLIF(TRIM(r.route_code),'') IS NULL OR NULLIF(TRIM(r.route_name),'') IS NULL
   OR r.enable_flag NOT IN ('Y','N');
INSERT INTO conversion_reject
SELECT 'ROUTING',r.route_id,'DUPLICATE_ROUTING_CODE'
FROM source_operation_routing.pro_route r
JOIN (SELECT TRIM(route_code) code FROM source_operation_routing.pro_route
      GROUP BY TRIM(route_code) HAVING COUNT(*)>1) d ON TRIM(r.route_code)=d.code;
INSERT INTO conversion_reject
SELECT DISTINCT 'ROUTING',s.route_id,'INVALID_STEP_REFERENCE_OR_RULE'
FROM source_operation_routing.pro_route_process s
LEFT JOIN source_operation_routing.pro_process o ON o.process_id=s.process_id
WHERE o.process_id IS NULL OR s.order_num IS NULL OR s.order_num<0
   OR COALESCE(s.link_type,'SS')<>'SS' OR COALESCE(s.key_flag,'N') NOT IN ('Y','N')
   OR NULLIF(TRIM(o.process_code),'') IS NULL OR NULLIF(TRIM(o.process_name),'') IS NULL
   OR COALESCE(o.enable_flag,'') NOT IN ('Y','N')
   OR EXISTS (SELECT 1 FROM source_operation_routing.pro_process duplicate_operation
              WHERE TRIM(duplicate_operation.process_code)=TRIM(o.process_code)
                AND duplicate_operation.process_id<>o.process_id);
INSERT INTO conversion_reject
SELECT 'ROUTING',route_id,'DUPLICATE_SOURCE_ORDER'
FROM source_operation_routing.pro_route_process
GROUP BY route_id HAVING COUNT(*)<>COUNT(DISTINCT order_num);
INSERT INTO conversion_reject
SELECT 'ROUTING',route_id,'TOO_MANY_STEPS_OR_KEY_POINTS'
FROM source_operation_routing.pro_route_process
GROUP BY route_id HAVING COUNT(*)>100 OR SUM(key_flag='Y')>1;
INSERT INTO conversion_reject
SELECT DISTINCT 'ROUTING',p.route_id,'MISSING_MATERIAL_REFERENCE'
FROM source_operation_routing.pro_route_product p
LEFT JOIN material m ON m.factory_id=@target_factory AND m.code=TRIM(p.item_code)
WHERE m.id IS NULL;
INSERT INTO conversion_reject
SELECT 'ROUTING',route_id,'DUPLICATE_OR_TOO_MANY_PRODUCTS'
FROM source_operation_routing.pro_route_product
GROUP BY route_id HAVING COUNT(*)>100 OR COUNT(*)<>COUNT(DISTINCT item_code);

START TRANSACTION;
INSERT INTO operation(factory_id,code,name,description,status,sort,external_id,revision)
SELECT @target_factory,TRIM(p.process_code),TRIM(p.process_name),NULLIF(p.attention,''),
       IF(p.enable_flag='Y','ENABLED','DISABLED'),0,NULL,0
FROM source_operation_routing.pro_process p
WHERE NOT EXISTS (SELECT 1 FROM conversion_reject x WHERE x.entity_type='OPERATION' AND x.source_id=p.process_id);

-- 来源启用不等于已通过 Base 发布审查，所有可转换路线作为版本一草稿进入。
INSERT INTO routing(factory_id,code,name,version,status,description,external_id,source_version,revision,published_at)
SELECT @target_factory,TRIM(r.route_code),TRIM(r.route_name),1,'DRAFT',NULLIF(r.route_desc,''),NULL,NULL,0,NULL
FROM source_operation_routing.pro_route r
WHERE NOT EXISTS (SELECT 1 FROM conversion_reject x WHERE x.entity_type='ROUTING' AND x.source_id=r.route_id);

INSERT INTO routing_node(routing_id,node_code,display_order,operation_id,operation_code,operation_name,is_key,description)
SELECT r.id,CONCAT('SRC_',s.record_id),s.normalized_sequence,o.id,NULL,NULL,IF(s.key_flag='Y',TRUE,FALSE),NULLIF(s.remark,'')
FROM (SELECT original.*,ROW_NUMBER() OVER(PARTITION BY route_id ORDER BY order_num,record_id) normalized_sequence
      FROM source_operation_routing.pro_route_process original) s
JOIN source_operation_routing.pro_route sr ON sr.route_id=s.route_id
JOIN routing r ON r.factory_id=@target_factory AND r.code=TRIM(sr.route_code) AND r.version=1
JOIN source_operation_routing.pro_process so ON so.process_id=s.process_id
JOIN operation o ON o.factory_id=@target_factory AND o.code=TRIM(so.process_code);

-- 来源排序仅用于转换相邻关系；最终执行拓扑只读连接边。
INSERT INTO routing_edge(routing_id,from_node_id,to_node_id)
SELECT routing_id,previous_id,id
FROM (SELECT n.routing_id,n.id,LAG(n.id) OVER(PARTITION BY n.routing_id ORDER BY n.display_order,n.node_code) previous_id
      FROM routing_node n JOIN routing r ON r.id=n.routing_id WHERE r.factory_id=@target_factory) ordered_nodes
WHERE previous_id IS NOT NULL;

INSERT INTO routing_product(routing_id,material_id,material_code,material_name)
SELECT r.id,m.id,NULL,NULL
FROM source_operation_routing.pro_route_product p
JOIN source_operation_routing.pro_route sr ON sr.route_id=p.route_id
JOIN routing r ON r.factory_id=@target_factory AND r.code=TRIM(sr.route_code) AND r.version=1
JOIN material m ON m.factory_id=@target_factory AND m.code=TRIM(p.item_code);
COMMIT;

SELECT 'CONVERSION_REJECT' result,entity_type,source_id,reason
FROM conversion_reject ORDER BY entity_type,source_id,reason;
SELECT 'OPERATION_COUNT' result,COUNT(*) count FROM operation WHERE factory_id=@target_factory;
SELECT 'ROUTING_COUNT' result,COUNT(*) count FROM routing WHERE factory_id=@target_factory;
SELECT 'NODE_COUNT' result,COUNT(*) count FROM routing_node s JOIN routing r ON r.id=s.routing_id WHERE r.factory_id=@target_factory;
SELECT 'PRODUCT_COUNT' result,COUNT(*) count FROM routing_product p JOIN routing r ON r.id=p.routing_id WHERE r.factory_id=@target_factory;

SELECT 'EDGE_COUNT' result,COUNT(*) count FROM routing_edge e JOIN routing r ON r.id=e.routing_id WHERE r.factory_id=@target_factory;
