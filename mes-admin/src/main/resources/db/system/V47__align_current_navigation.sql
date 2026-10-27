-- 当前有效导航与权限定义按业务语义归并，不复制主键、账号、工厂角色或任何角色授权。
-- 保留既有menu_id及role_menu；失效入口仅禁用留审计，聚合页按钮仍保留原权限。
-- 全部菜单DML在一个事务内：角色权限不得新增，除明确退役入口外不得减少。
DELIMITER $$
CREATE PROCEDURE mes_v47_align_current_navigation()
BEGIN
    DECLARE expected_count INT;
    DECLARE resolved_count INT;
    DECLARE inserted_count INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    CREATE TEMPORARY TABLE mes_v47_definitions (
        menu_key VARCHAR(512) COLLATE utf8mb4_bin PRIMARY KEY,
        parent_key VARCHAR(512) COLLATE utf8mb4_bin NULL,
        menu_name VARCHAR(50) NOT NULL, order_num INT NOT NULL,
        path VARCHAR(200) NOT NULL, component VARCHAR(255) NULL,
        `query` VARCHAR(255) NULL, route_name VARCHAR(200) NULL,
        is_frame CHAR(1) NOT NULL, is_cache CHAR(1) NOT NULL,
        menu_type CHAR(1) NOT NULL, visible CHAR(1) NOT NULL,
        status CHAR(1) NOT NULL, perms VARCHAR(100) NULL, icon VARCHAR(100) NULL
    ) CHARACTER SET utf8mb4;
    CREATE TEMPORARY TABLE mes_v47_bindings (
        menu_key VARCHAR(512) COLLATE utf8mb4_bin PRIMARY KEY, menu_id BIGINT NOT NULL
    ) CHARACTER SET utf8mb4;
    CREATE TEMPORARY TABLE mes_v47_parents LIKE mes_v47_bindings;
    CREATE TEMPORARY TABLE mes_v47_previous_permissions (
        role_id BIGINT NOT NULL, perms VARCHAR(100) NOT NULL, PRIMARY KEY(role_id,perms)
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
    INSERT INTO mes_v47_definitions (`menu_key`,`parent_key`,`menu_name`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`) VALUES
    ('C:basic/equipment/index','M:basic','设备','0','equipment','basic/equipment/index',NULL,'','1','0','C','0','0','business:equipment:read','list'),
    ('C:basic/materialCategory/index','M:basic','物料分类','1','materialCategory','basic/materialCategory/index',NULL,'','1','0','C','0','0','business:material:category:list','list'),
    ('C:basic/materials/index','M:basic','物料管理','0','materials','basic/materials/index',NULL,'','1','0','C','0','0',NULL,'list'),
    ('C:basic/safetyStock/index','M:basic','安全库存配置','0','safetyStock','basic/safetyStock/index',NULL,'','1','0','C','0','0','business:safety:stock:list','list'),
    ('C:basic/unitMeasure/index','M:basic','计量单位','2','unitMeasure','basic/unitMeasure/index',NULL,'','1','0','C','0','0','business:unit:measure:list','list'),
    ('C:basic/workshop/index','M:basic','车间','0','workshop','basic/workshop/index',NULL,'','1','0','C','0','0','business:workshop:list','list'),
    ('C:basic/workstation/index','M:basic','工作站','0','workstation','basic/workstation/index',NULL,'','1','0','C','0','0','business:workstation:list','list'),
    ('C:dv/dvSubject/index','M:basic','设备点检保养项目','0','dvSubject','dv/dvSubject/index',NULL,'','1','0','C','0','0','business:dv:subject:list','list'),
    ('C:issue/mesMaterialIssue/index','M:issue','领料出库','0','mesMaterialIssue','issue/mesMaterialIssue/index',NULL,'','1','0','C','0','0','business:mes:material:issue:list','list'),
    ('C:issue/mesMaterialIssueItem/index','M:issue','领料出库明细','0','mesMaterialIssueItem','issue/mesMaterialIssueItem/index',NULL,'','1','0','C','1','0','business:mes:material:issue:item:list','list'),
    ('C:monitor/cache/index','M:monitor','缓存监控','13','cache','monitor/cache/index',NULL,'','1','0','C','0','0','monitor:cache:list','#'),
    ('C:monitor/druid/index','M:monitor','Druid 监控','14','druid','monitor/druid/index',NULL,'','1','0','C','0','0','monitor:druid:list','#'),
    ('C:monitor/job/index','M:monitor','任务调度','11','job','monitor/job/index',NULL,'','1','0','C','0','0','monitor:job:list','#'),
    ('C:monitor/logininfor/index','M:monitor','登录日志','9','logininfor','monitor/logininfor/index',NULL,'','1','0','C','0','0','monitor:logininfor:list','#'),
    ('C:monitor/online/index','M:monitor','在线用户','10','online','monitor/online/index',NULL,'','1','0','C','0','0','monitor:online:list','#'),
    ('C:monitor/operlog/index','M:monitor','操作日志','8','operlog','monitor/operlog/index',NULL,'','1','0','C','0','0','monitor:operlog:list','#'),
    ('C:monitor/server/index','M:monitor','服务监控','12','server','monitor/server/index',NULL,'','1','0','C','0','0','monitor:server:list','#'),
    ('C:production/mesExecutionAssignment/index','M:production','生产节点派工','0','mesExecutionAssignment','production/mesExecutionAssignment/index',NULL,'','1','0','C','1','0','business:mes:execution:assignment:list','list'),
    ('C:production/mesOperationUser/index','M:production','工序能力人员','0','mesOperationUser','production/mesOperationUser/index',NULL,'','1','0','C','1','0','business:mes:operation:user:list','list'),
    ('C:production/mesProductionExecution/index','M:production','生产执行','0','mesProductionExecution','production/mesProductionExecution/index',NULL,'','1','0','C','0','0','business:mes:production:execution:list','list'),
    ('C:production/operation/index','M:production','工序','0','operation','production/operation/index',NULL,'','1','0','C','0','0','operation:list','list'),
    ('C:production/plan/index','M:production','生产计划','1','plan','production/plan/index',NULL,'','1','0','C','0','0','business:mes:production:plan:list','Calendar'),
    ('C:production/productionInput/index','M:production','扫码投产','35','productionInput','production/productionInput/index',NULL,'','1','1','C','0','0','business:mes:material:issue:update','list'),
    ('C:production/productionReceipt/index','M:production','旧流程物流历史','50','productionReceipt','production/productionReceipt/index',NULL,'','1','1','C','1','0','business:production:receipt:list','list'),
    ('C:production/productionReport/index','M:production','生产数量管理','30','productionReport','production/productionReport/index',NULL,'','1','1','C','0','0','business:production:report:quantity','list'),
    ('C:production/productionReporting/index','M:production','生产报工','40','productionReporting','production/productionReporting/index',NULL,'','1','1','C','0','0','business:production:report:submit','list'),
    ('C:production/routing/index','M:production','工艺路线','0','routing','production/routing/index',NULL,'','1','0','C','0','0','routing:list','list'),
    ('C:production/workorder/index','M:production','生产工单','0','workorder','production/workorder/index',NULL,'','1','0','C','0','0','workorder:list','list'),
    ('C:quality/productionDefect/index','M:quality','不良品管理','10','productionDefect','quality/productionDefect/index',NULL,'','1','1','C','0','0','business:quality:defect:read','list'),
    ('C:sales/customer/index','M:sales','客户管理','1','customer','sales/customer/index',NULL,'','1','0','C','0','0','business:mes:customer:list','User'),
    ('C:sales/order/index','M:sales','销售订单','2','order','sales/order/index',NULL,'','1','0','C','0','0','business:mes:sales:order:list','Document'),
    ('C:system/autocode/index','M:system','自动编码规则','20','autocode','system/autocode/index',NULL,'','1','0','C','0','0','system:autocode:rule:list','#'),
    ('C:system/config/index','M:system','参数设置','6','config','system/config/index',NULL,'','1','0','C','0','0','system:config:list','#'),
    ('C:system/datasource/index','M:system','数据源管理','31','datasource','system/datasource/index',NULL,'','1','0','C','0','0','system:datasource:list','#'),
    ('C:system/dept/index','M:system','部门管理','3','dept','system/dept/index',NULL,'','1','0','C','0','0','system:dept:list','#'),
    ('C:system/dict/index','M:system','字典管理','5','dict','system/dict/index',NULL,'','1','0','C','0','0','system:dict:list','#'),
    ('C:system/factory/index','M:system','工厂管理','30','factory','system/factory/index',NULL,'','1','0','C','0','0','system:factory:list','#'),
    ('C:system/menu/index','M:system','菜单管理','2','menu','system/menu/index',NULL,'','1','0','C','0','0','system:menu:list','#'),
    ('C:system/message/index','M:system','消息管理','10','message','system/message/index',NULL,'','1','0','C','0','0','system:message:list','#'),
    ('C:system/notice/index','M:system','通知公告','7','notice','system/notice/index',NULL,'','1','0','C','0','0','system:notice:list','#'),
    ('C:system/openApiClient/index','M:system','OpenAPI应用','16','openApiClient','system/openApiClient/index',NULL,'','1','0','C','0','0','system:openApiClient:list','#'),
    ('C:system/post/index','M:system','岗位管理','4','post','system/post/index',NULL,'','1','0','C','0','0','system:post:list','#'),
    ('C:system/role/index','M:system','角色管理','1','role','system/role/index',NULL,'','1','0','C','0','0','system:role:list','#'),
    ('C:system/user/index','M:system','用户管理','0','user','system/user/index',NULL,'','1','0','C','0','0','system:user:list','#'),
    ('C:tool/swagger/index','M:tool','系统接口','1','swagger','tool/swagger/index',NULL,'','1','0','C','0','0','tool:swagger:view','#'),
    ('C:wm/stockDocuments/index','M:stock','出入库单','30','stockDocuments','wm/stockDocuments/index',NULL,'','1','1','C','0','0','business:wm:stock:document:list','#'),
    ('C:wm/wmInventory/index','M:stock','库存管理','1','wmInventory','wm/wmInventory/index',NULL,'','1','0','C','0','0','business:wm:inventory:list','list'),
    ('C:wm/wmWarehouse/index','M:stock','仓库管理','0','wmWarehouse','wm/wmWarehouse/index',NULL,'','1','0','C','0','0','business:wm:warehouse:list','list'),
    ('F:batch:query','C:production/workorder/index','批次详情','20','#',NULL,NULL,'','1','0','F','0','0','batch:query','#'),
    ('F:batch:selectRoute','C:production/workorder/index','选择批次路径','20','#',NULL,NULL,'','1','0','F','0','0','batch:selectRoute','#'),
    ('F:business:directory:department:read',NULL,'业务部门查询','2','business/directory/departments',NULL,NULL,'','1','0','F','0','0','business:directory:department:read','#'),
    ('F:business:directory:personnel:read',NULL,'业务人员查询','1','business/directory/personnel',NULL,NULL,'','1','0','F','0','0','business:directory:personnel:read','#'),
    ('F:business:directory:post:read',NULL,'业务岗位查询','3','business/directory/posts',NULL,NULL,'','1','0','F','0','0','business:directory:post:read','#'),
    ('F:business:dv:subject:create','C:dv/dvSubject/index','设备点检保养项目新增','2','#',NULL,NULL,'','1','0','F','0','0','business:dv:subject:create','#'),
    ('F:business:dv:subject:delete','C:dv/dvSubject/index','设备点检保养项目删除','4','#',NULL,NULL,'','1','0','F','0','0','business:dv:subject:delete','#'),
    ('F:business:dv:subject:list','C:dv/dvSubject/index','设备点检保养项目列表','0','#',NULL,NULL,'','1','0','F','0','0','business:dv:subject:list','#'),
    ('F:business:dv:subject:query','C:dv/dvSubject/index','设备点检保养项目详情','1','#',NULL,NULL,'','1','0','F','0','0','business:dv:subject:query','#'),
    ('F:business:dv:subject:sync','C:dv/dvSubject/index','设备点检保养项目同步','5','#',NULL,NULL,'','1','0','F','0','0','business:dv:subject:sync','#'),
    ('F:business:dv:subject:update','C:dv/dvSubject/index','设备点检保养项目修改','3','#',NULL,NULL,'','1','0','F','0','0','business:dv:subject:update','#'),
    ('F:business:equipment:create','C:basic/equipment/index','设备新增','1','#',NULL,NULL,'','1','0','F','0','0','business:equipment:create','#'),
    ('F:business:equipment:delete','C:basic/equipment/index','设备删除','3','#',NULL,NULL,'','1','0','F','0','0','business:equipment:delete','#'),
    ('F:business:equipment:read','C:basic/equipment/index','设备查询','0','#',NULL,NULL,'','1','0','F','0','0','business:equipment:read','#'),
    ('F:business:equipment:update','C:basic/equipment/index','设备修改','2','#',NULL,NULL,'','1','0','F','0','0','business:equipment:update','#'),
    ('F:business:material:category:create','C:basic/materialCategory/index','物料分类新增','2','#',NULL,NULL,'','1','0','F','0','0','business:material:category:create','#'),
    ('F:business:material:category:delete','C:basic/materialCategory/index','物料分类删除','4','#',NULL,NULL,'','1','0','F','0','0','business:material:category:delete','#'),
    ('F:business:material:category:list','C:basic/materialCategory/index','物料分类列表','0','#',NULL,NULL,'','1','0','F','0','0','business:material:category:list','#'),
    ('F:business:material:category:query','C:basic/materialCategory/index','物料分类详情','1','#',NULL,NULL,'','1','0','F','0','0','business:material:category:query','#'),
    ('F:business:material:category:sync','C:basic/materialCategory/index','物料分类同步','5','#',NULL,NULL,'','1','0','F','0','0','business:material:category:sync','#'),
    ('F:business:material:category:update','C:basic/materialCategory/index','物料分类修改','3','#',NULL,NULL,'','1','0','F','0','0','business:material:category:update','#'),
    ('F:business:material:create','C:basic/materials/index','物料新增','2','#',NULL,NULL,'','1','0','F','0','0','business:material:create','#'),
    ('F:business:material:delete','C:basic/materials/index','物料删除','4','#',NULL,NULL,'','1','0','F','0','0','business:material:delete','#'),
    ('F:business:material:list','C:basic/materials/index','物料列表','0','#',NULL,NULL,'','1','0','F','0','0','business:material:list','#'),
    ('F:business:material:query','C:basic/materials/index','物料详情','1','#',NULL,NULL,'','1','0','F','0','0','business:material:query','#'),
    ('F:business:material:sync','C:basic/materials/index','物料同步','5','#',NULL,NULL,'','1','0','F','0','0','business:material:sync','#'),
    ('F:business:material:update','C:basic/materials/index','物料修改','3','#',NULL,NULL,'','1','0','F','0','0','business:material:update','#'),
    ('F:business:mes:customer:create','C:sales/customer/index','销售客户新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:customer:create','#'),
    ('F:business:mes:customer:delete','C:sales/customer/index','销售客户删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:customer:delete','#'),
    ('F:business:mes:customer:list','C:sales/customer/index','销售客户列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:customer:list','#'),
    ('F:business:mes:customer:query','C:sales/customer/index','销售客户详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:customer:query','#'),
    ('F:business:mes:customer:update','C:sales/customer/index','销售客户修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:customer:update','#'),
    ('F:business:mes:execution:assignment:create','C:production/mesProductionExecution/index','生产节点派工新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:execution:assignment:create','#'),
    ('F:business:mes:execution:assignment:delete','C:production/mesProductionExecution/index','生产节点派工删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:execution:assignment:delete','#'),
    ('F:business:mes:execution:assignment:list','C:production/mesProductionExecution/index','生产节点派工列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:execution:assignment:list','#'),
    ('F:business:mes:execution:assignment:query','C:production/mesProductionExecution/index','生产节点派工详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:execution:assignment:query','#'),
    ('F:business:mes:execution:assignment:update','C:production/mesProductionExecution/index','生产节点派工修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:execution:assignment:update','#'),
    ('F:business:mes:material:issue:create','C:issue/mesMaterialIssue/index','领料出库单新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:create','#'),
    ('F:business:mes:material:issue:delete','C:issue/mesMaterialIssue/index','领料出库单删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:delete','#'),
    ('F:business:mes:material:issue:erp:query','C:issue/mesMaterialIssue/index','查询ERP状态','9','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:erp:query','#'),
    ('F:business:mes:material:issue:erp:reconcile','C:issue/mesMaterialIssue/index','核实并同步ERP状态','10','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:erp:reconcile','#'),
    ('F:business:mes:material:issue:item:create','C:issue/mesMaterialIssue/index','领料出库明细新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:item:create','#'),
    ('F:business:mes:material:issue:item:delete','C:issue/mesMaterialIssue/index','领料出库明细删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:item:delete','#'),
    ('F:business:mes:material:issue:item:list','C:issue/mesMaterialIssue/index','领料出库明细列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:item:list','#'),
    ('F:business:mes:material:issue:item:query','C:issue/mesMaterialIssue/index','领料出库明细详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:item:query','#'),
    ('F:business:mes:material:issue:item:update','C:issue/mesMaterialIssue/index','领料出库明细修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:item:update','#'),
    ('F:business:mes:material:issue:list','C:issue/mesMaterialIssue/index','领料出库单列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:list','#'),
    ('F:business:mes:material:issue:query','C:issue/mesMaterialIssue/index','领料出库单详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:query','#'),
    ('F:business:mes:material:issue:update','C:issue/mesMaterialIssue/index','领料出库单修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:update','#'),
    ('F:business:mes:material:issue:void','C:issue/mesMaterialIssue/index','领料作废','8','#',NULL,NULL,'','1','0','F','0','0','business:mes:material:issue:void','#'),
    ('F:business:mes:operation:user:create','C:production/operation/index','工序能力人员新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:operation:user:create','#'),
    ('F:business:mes:operation:user:delete','C:production/operation/index','工序能力人员删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:operation:user:delete','#'),
    ('F:business:mes:operation:user:list','C:production/operation/index','工序能力人员列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:operation:user:list','#'),
    ('F:business:mes:operation:user:query','C:production/operation/index','工序能力人员详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:operation:user:query','#'),
    ('F:business:mes:operation:user:update','C:production/operation/index','工序能力人员修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:operation:user:update','#'),
    ('F:business:mes:production:execution:delete','C:production/mesProductionExecution/index','生产执行单元删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:execution:delete','#'),
    ('F:business:mes:production:execution:list','C:production/mesProductionExecution/index','生产执行单元列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:execution:list','#'),
    ('F:business:mes:production:execution:query','C:production/mesProductionExecution/index','生产执行单元详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:execution:query','#'),
    ('F:business:mes:production:execution:update','C:production/mesProductionExecution/index','生产执行单元修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:execution:update','#'),
    ('F:business:mes:production:plan:create','C:production/plan/index','生产计划新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:plan:create','#'),
    ('F:business:mes:production:plan:delete','C:production/plan/index','生产计划删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:plan:delete','#'),
    ('F:business:mes:production:plan:list','C:production/plan/index','生产计划列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:plan:list','#'),
    ('F:business:mes:production:plan:query','C:production/plan/index','生产计划详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:plan:query','#'),
    ('F:business:mes:production:plan:update','C:production/plan/index','生产计划修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:production:plan:update','#'),
    ('F:business:mes:sales:order:create','C:sales/order/index','销售订单新增','2','#',NULL,NULL,'','1','0','F','0','0','business:mes:sales:order:create','#'),
    ('F:business:mes:sales:order:delete','C:sales/order/index','销售订单删除','4','#',NULL,NULL,'','1','0','F','0','0','business:mes:sales:order:delete','#'),
    ('F:business:mes:sales:order:list','C:sales/order/index','销售订单列表','0','#',NULL,NULL,'','1','0','F','0','0','business:mes:sales:order:list','#'),
    ('F:business:mes:sales:order:query','C:sales/order/index','销售订单详情','1','#',NULL,NULL,'','1','0','F','0','0','business:mes:sales:order:query','#'),
    ('F:business:mes:sales:order:update','C:sales/order/index','销售订单修改','3','#',NULL,NULL,'','1','0','F','0','0','business:mes:sales:order:update','#'),
    ('F:business:operation:defect:type:create','C:production/operation/index','创建工序不良类型','1','#',NULL,NULL,'','1','1','F','0','0','business:operation:defect:type:create','#'),
    ('F:business:operation:defect:type:delete','C:production/operation/index','删除工序不良类型','3','#',NULL,NULL,'','1','1','F','0','0','business:operation:defect:type:delete','#'),
    ('F:business:operation:defect:type:read','C:production/operation/index','查看工序不良类型','0','#',NULL,NULL,'','1','1','F','0','0','business:operation:defect:type:read','#'),
    ('F:business:operation:defect:type:update','C:production/operation/index','修改工序不良类型','2','#',NULL,NULL,'','1','1','F','0','0','business:operation:defect:type:update','#'),
    ('F:business:production:execution:complete','C:production/mesProductionExecution/index','复核入库收尾','47','#',NULL,NULL,'','1','1','F','0','0','business:production:execution:complete','#'),
    ('F:business:production:receipt:detail','C:production/productionReceipt/index','旧流程物流详情','1','#',NULL,NULL,'','1','1','F','0','0','business:production:receipt:detail','#'),
    ('F:business:production:report:adjust','C:production/productionReport/index','管理员报工数量订正','30','#',NULL,NULL,'','1','1','F','0','0','business:production:report:adjust','#'),
    ('F:business:production:report:detail','C:production/productionReport/index','报工流水及调整历史查询','30','#',NULL,NULL,'','1','1','F','0','0','business:production:report:detail','#'),
    ('F:business:production:report:supplement','C:production/productionReport/index','管理员历史报工补录','30','#',NULL,NULL,'','1','1','F','0','0','business:production:report:supplement','#'),
    ('F:business:production:report:void','C:production/productionReport/index','管理员原始报工整笔冲销','30','#',NULL,NULL,'','1','1','F','0','0','business:production:report:void','#'),
    ('F:business:quality:defect:create','C:quality/productionDefect/index','不良申报','1','#',NULL,NULL,'','1','1','F','0','0','business:quality:defect:create','#'),
    ('F:business:quality:defect:revise','C:quality/productionDefect/index','管理员留痕订正','2','#',NULL,NULL,'','1','1','F','0','0','business:quality:defect:revise','#'),
    ('F:business:quality:defect:void','C:quality/productionDefect/index','管理员留痕作废','3','#',NULL,NULL,'','1','1','F','0','0','business:quality:defect:void','#'),
    ('F:business:quality:defect:waste:override','C:quality/productionDefect/index','覆盖申报工废/料废类别','4','#',NULL,NULL,'','1','1','F','0','0','business:quality:defect:waste:override','#'),
    ('F:business:quality:release:read','C:quality/productionDefect/index','质量放行历史','41','#',NULL,NULL,'','1','1','F','0','0','business:quality:release:read','#'),
    ('F:business:safety:stock:create','C:basic/safetyStock/index','安全库存配置新增','2','#',NULL,NULL,'','1','0','F','0','0','business:safety:stock:create','#'),
    ('F:business:safety:stock:delete','C:basic/safetyStock/index','安全库存配置删除','4','#',NULL,NULL,'','1','0','F','0','0','business:safety:stock:delete','#'),
    ('F:business:safety:stock:list','C:basic/safetyStock/index','安全库存配置列表','0','#',NULL,NULL,'','1','0','F','0','0','business:safety:stock:list','#'),
    ('F:business:safety:stock:query','C:basic/safetyStock/index','安全库存配置详情','1','#',NULL,NULL,'','1','0','F','0','0','business:safety:stock:query','#'),
    ('F:business:safety:stock:sync','C:basic/safetyStock/index','安全库存配置同步','5','#',NULL,NULL,'','1','0','F','0','0','business:safety:stock:sync','#'),
    ('F:business:safety:stock:update','C:basic/safetyStock/index','安全库存配置修改','3','#',NULL,NULL,'','1','0','F','0','0','business:safety:stock:update','#'),
    ('F:business:unit:measure:create','C:basic/unitMeasure/index','计量单位新增','2','#',NULL,NULL,'','1','0','F','0','0','business:unit:measure:create','#'),
    ('F:business:unit:measure:delete','C:basic/unitMeasure/index','计量单位删除','4','#',NULL,NULL,'','1','0','F','0','0','business:unit:measure:delete','#'),
    ('F:business:unit:measure:list','C:basic/unitMeasure/index','计量单位列表','0','#',NULL,NULL,'','1','0','F','0','0','business:unit:measure:list','#'),
    ('F:business:unit:measure:query','C:basic/unitMeasure/index','计量单位详情','1','#',NULL,NULL,'','1','0','F','0','0','business:unit:measure:query','#'),
    ('F:business:unit:measure:sync','C:basic/unitMeasure/index','计量单位同步','5','#',NULL,NULL,'','1','0','F','0','0','business:unit:measure:sync','#'),
    ('F:business:unit:measure:update','C:basic/unitMeasure/index','计量单位修改','3','#',NULL,NULL,'','1','0','F','0','0','business:unit:measure:update','#'),
    ('F:business:wm:acceptance:read','C:wm/wmInventory/index','历史入库追溯','44','#',NULL,NULL,'','1','1','F','0','0','business:wm:acceptance:read','#'),
    ('F:business:wm:inventory:create','C:wm/wmInventory/index','库存管理新增','2','#',NULL,NULL,'','1','0','F','0','0','business:wm:inventory:create','#'),
    ('F:business:wm:inventory:delete','C:wm/wmInventory/index','库存管理删除','4','#',NULL,NULL,'','1','0','F','0','0','business:wm:inventory:delete','#'),
    ('F:business:wm:inventory:list','C:wm/wmInventory/index','库存管理列表','0','#',NULL,NULL,'','1','0','F','0','0','business:wm:inventory:list','#'),
    ('F:business:wm:inventory:query','C:wm/wmInventory/index','库存管理详情','1','#',NULL,NULL,'','1','0','F','0','0','business:wm:inventory:query','#'),
    ('F:business:wm:inventory:sync','C:wm/wmInventory/index','库存管理同步','5','#',NULL,NULL,'','1','0','F','0','0','business:wm:inventory:sync','#'),
    ('F:business:wm:inventory:update','C:wm/wmInventory/index','库存管理修改','3','#',NULL,NULL,'','1','0','F','0','0','business:wm:inventory:update','#'),
    ('F:business:wm:stock:action:list','C:wm/wmInventory/index','库存动作流水','26','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:action:list','#'),
    ('F:business:wm:stock:action:query','C:wm/wmInventory/index','库存动作详情','27','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:action:query','#'),
    ('F:business:wm:stock:correction','C:wm/wmInventory/index','库存业务纠错','24','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:correction','#'),
    ('F:business:wm:stock:document:cancel','C:wm/stockDocuments/index','作废草稿','3','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:document:cancel','#'),
    ('F:business:wm:stock:document:create','C:wm/stockDocuments/index','创建草稿','1','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:document:create','#'),
    ('F:business:wm:stock:document:post','C:wm/stockDocuments/index','正式过账','4','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:document:post','#'),
    ('F:business:wm:stock:document:query','C:wm/stockDocuments/index','查看单据','0','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:document:query','#'),
    ('F:business:wm:stock:document:update','C:wm/stockDocuments/index','维护草稿及明细','2','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:document:update','#'),
    ('F:business:wm:stock:gain','C:wm/wmInventory/index','库存盘盈','22','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:gain','#'),
    ('F:business:wm:stock:loss','C:wm/wmInventory/index','库存盘亏','23','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:loss','#'),
    ('F:business:wm:stock:opening','C:wm/wmInventory/index','期初建账','20','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:opening','#'),
    ('F:business:wm:stock:receipt','C:wm/wmInventory/index','历史原料入库核实','21','#',NULL,NULL,'','1','1','F','1','0','business:wm:stock:receipt','#'),
    ('F:business:wm:stock:return','C:wm/wmInventory/index','生产退料','25','#',NULL,NULL,'','1','1','F','0','0','business:wm:stock:return','#'),
    ('F:business:wm:warehouse:create','C:wm/wmWarehouse/index','仓库管理新增','2','#',NULL,NULL,'','1','0','F','0','0','business:wm:warehouse:create','#'),
    ('F:business:wm:warehouse:delete','C:wm/wmWarehouse/index','仓库管理删除','4','#',NULL,NULL,'','1','0','F','0','0','business:wm:warehouse:delete','#'),
    ('F:business:wm:warehouse:list','C:wm/wmWarehouse/index','仓库管理列表','0','#',NULL,NULL,'','1','0','F','0','0','business:wm:warehouse:list','#'),
    ('F:business:wm:warehouse:query','C:wm/wmWarehouse/index','仓库管理详情','1','#',NULL,NULL,'','1','0','F','0','0','business:wm:warehouse:query','#'),
    ('F:business:wm:warehouse:sync','C:wm/wmWarehouse/index','仓库管理同步','5','#',NULL,NULL,'','1','0','F','0','0','business:wm:warehouse:sync','#'),
    ('F:business:wm:warehouse:update','C:wm/wmWarehouse/index','仓库管理修改','3','#',NULL,NULL,'','1','0','F','0','0','business:wm:warehouse:update','#'),
    ('F:business:workshop:create','C:basic/workshop/index','车间新增','2','#',NULL,NULL,'','1','0','F','0','0','business:workshop:create','#'),
    ('F:business:workshop:delete','C:basic/workshop/index','车间删除','4','#',NULL,NULL,'','1','0','F','0','0','business:workshop:delete','#'),
    ('F:business:workshop:list','C:basic/workshop/index','车间列表','0','#',NULL,NULL,'','1','0','F','0','0','business:workshop:list','#'),
    ('F:business:workshop:query','C:basic/workshop/index','车间详情','1','#',NULL,NULL,'','1','0','F','0','0','business:workshop:query','#'),
    ('F:business:workshop:sync','C:basic/workshop/index','车间同步','5','#',NULL,NULL,'','1','0','F','0','0','business:workshop:sync','#'),
    ('F:business:workshop:update','C:basic/workshop/index','车间修改','3','#',NULL,NULL,'','1','0','F','0','0','business:workshop:update','#'),
    ('F:business:workstation:create','C:basic/workstation/index','工作站新增','2','#',NULL,NULL,'','1','0','F','0','0','business:workstation:create','#'),
    ('F:business:workstation:delete','C:basic/workstation/index','工作站删除','4','#',NULL,NULL,'','1','0','F','0','0','business:workstation:delete','#'),
    ('F:business:workstation:list','C:basic/workstation/index','工作站列表','0','#',NULL,NULL,'','1','0','F','0','0','business:workstation:list','#'),
    ('F:business:workstation:query','C:basic/workstation/index','工作站详情','1','#',NULL,NULL,'','1','0','F','0','0','business:workstation:query','#'),
    ('F:business:workstation:sync','C:basic/workstation/index','工作站同步','5','#',NULL,NULL,'','1','0','F','0','0','business:workstation:sync','#'),
    ('F:business:workstation:update','C:basic/workstation/index','工作站修改','3','#',NULL,NULL,'','1','0','F','0','0','business:workstation:update','#'),
    ('F:monitor:job:add','C:monitor/job/index','任务调度 · 新增','0','',NULL,NULL,'','1','0','F','0','0','monitor:job:add','#'),
    ('F:monitor:job:changeStatus','C:monitor/job/index','任务调度 · 状态','0','',NULL,NULL,'','1','0','F','0','0','monitor:job:changeStatus','#'),
    ('F:monitor:job:edit','C:monitor/job/index','任务调度 · 修改','0','',NULL,NULL,'','1','0','F','0','0','monitor:job:edit','#'),
    ('F:monitor:job:export','C:monitor/job/index','任务调度 · 导出','0','',NULL,NULL,'','1','0','F','0','0','monitor:job:export','#'),
    ('F:monitor:job:query','C:monitor/job/index','任务调度 · 查询','0','',NULL,NULL,'','1','0','F','0','0','monitor:job:query','#'),
    ('F:monitor:job:remove','C:monitor/job/index','任务调度 · 删除','0','',NULL,NULL,'','1','0','F','0','0','monitor:job:remove','#'),
    ('F:monitor:logininfor:export','C:monitor/logininfor/index','登录日志 · 导出','0','',NULL,NULL,'','1','0','F','0','0','monitor:logininfor:export','#'),
    ('F:monitor:logininfor:remove','C:monitor/logininfor/index','登录日志 · 删除','0','',NULL,NULL,'','1','0','F','0','0','monitor:logininfor:remove','#'),
    ('F:monitor:online:forceLogout','C:monitor/online/index','在线用户 · 强退','0','',NULL,NULL,'','1','0','F','0','0','monitor:online:forceLogout','#'),
    ('F:monitor:operlog:export','C:monitor/operlog/index','操作日志 · 导出','0','',NULL,NULL,'','1','0','F','0','0','monitor:operlog:export','#'),
    ('F:monitor:operlog:remove','C:monitor/operlog/index','操作日志 · 删除','0','',NULL,NULL,'','1','0','F','0','0','monitor:operlog:remove','#'),
    ('F:operation:create','C:production/operation/index','工序新增','2','#',NULL,NULL,'','1','0','F','0','0','operation:create','#'),
    ('F:operation:delete','C:production/operation/index','工序删除','4','#',NULL,NULL,'','1','0','F','0','0','operation:delete','#'),
    ('F:operation:list','C:production/operation/index','工序列表','0','#',NULL,NULL,'','1','0','F','0','0','operation:list','#'),
    ('F:operation:query','C:production/operation/index','工序详情','1','#',NULL,NULL,'','1','0','F','0','0','operation:query','#'),
    ('F:operation:sync','C:production/operation/index','同步','10','#',NULL,NULL,'','1','0','F','0','0','operation:sync','#'),
    ('F:operation:update','C:production/operation/index','工序修改','3','#',NULL,NULL,'','1','0','F','0','0','operation:update','#'),
    ('F:production:workorder:cancel','C:production/workorder/index','正式作废工单','48','#',NULL,NULL,'','1','1','F','0','0','production:workorder:cancel','#'),
    ('F:routing:copy','C:production/routing/index','复制','10','#',NULL,NULL,'','1','0','F','0','0','routing:copy','#'),
    ('F:routing:create','C:production/routing/index','工艺路线新增','2','#',NULL,NULL,'','1','0','F','0','0','routing:create','#'),
    ('F:routing:delete','C:production/routing/index','工艺路线删除','4','#',NULL,NULL,'','1','0','F','0','0','routing:delete','#'),
    ('F:routing:list','C:production/routing/index','工艺路线列表','0','#',NULL,NULL,'','1','0','F','0','0','routing:list','#'),
    ('F:routing:publish','C:production/routing/index','发布与启停','10','#',NULL,NULL,'','1','0','F','0','0','routing:publish','#'),
    ('F:routing:query','C:production/routing/index','工艺路线详情','1','#',NULL,NULL,'','1','0','F','0','0','routing:query','#'),
    ('F:routing:sync','C:production/routing/index','同步','10','#',NULL,NULL,'','1','0','F','0','0','routing:sync','#'),
    ('F:routing:update','C:production/routing/index','工艺路线修改','3','#',NULL,NULL,'','1','0','F','0','0','routing:update','#'),
    ('F:routing:version','C:production/routing/index','创建新版本','10','#',NULL,NULL,'','1','0','F','0','0','routing:version','#'),
    ('F:system:autocode:part:insert','C:system/autocode/index','新增组成','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:part:insert','#'),
    ('F:system:autocode:part:list','C:system/autocode/index','查询组成','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:part:list','#'),
    ('F:system:autocode:part:query','C:system/autocode/index','组成详情','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:part:query','#'),
    ('F:system:autocode:part:remove','C:system/autocode/index','删除组成','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:part:remove','#'),
    ('F:system:autocode:part:update','C:system/autocode/index','修改组成','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:part:update','#'),
    ('F:system:autocode:rule:add','C:system/autocode/index','新增规则','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:rule:add','#'),
    ('F:system:autocode:rule:edit','C:system/autocode/index','修改规则','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:rule:edit','#'),
    ('F:system:autocode:rule:generate','C:system/autocode/index','生成编码','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:rule:generate','#'),
    ('F:system:autocode:rule:query','C:system/autocode/index','查询规则','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:rule:query','#'),
    ('F:system:autocode:rule:remove','C:system/autocode/index','删除规则','0','',NULL,NULL,'','1','0','F','0','0','system:autocode:rule:remove','#'),
    ('F:system:config:add','C:system/config/index','参数设置 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:config:add','#'),
    ('F:system:config:edit','C:system/config/index','参数设置 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:config:edit','#'),
    ('F:system:config:export','C:system/config/index','参数设置 · 导出','0','',NULL,NULL,'','1','0','F','0','0','system:config:export','#'),
    ('F:system:config:query','C:system/config/index','参数设置 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:config:query','#'),
    ('F:system:config:remove','C:system/config/index','参数设置 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:config:remove','#'),
    ('F:system:datasource:add','C:system/datasource/index','数据源管理新增','0','',NULL,NULL,'','1','0','F','0','0','system:datasource:add','#'),
    ('F:system:datasource:edit','C:system/datasource/index','数据源管理编辑','0','',NULL,NULL,'','1','0','F','0','0','system:datasource:edit','#'),
    ('F:system:datasource:query','C:system/datasource/index','数据源管理查询','0','',NULL,NULL,'','1','0','F','0','0','system:datasource:query','#'),
    ('F:system:datasource:remove','C:system/datasource/index','数据源管理删除','0','',NULL,NULL,'','1','0','F','0','0','system:datasource:remove','#'),
    ('F:system:dept:add','C:system/dept/index','部门管理 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:dept:add','#'),
    ('F:system:dept:edit','C:system/dept/index','部门管理 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:dept:edit','#'),
    ('F:system:dept:query','C:system/dept/index','部门管理 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:dept:query','#'),
    ('F:system:dept:remove','C:system/dept/index','部门管理 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:dept:remove','#'),
    ('F:system:dict:add','C:system/dict/index','字典管理 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:dict:add','#'),
    ('F:system:dict:edit','C:system/dict/index','字典管理 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:dict:edit','#'),
    ('F:system:dict:export','C:system/dict/index','字典管理 · 导出','0','',NULL,NULL,'','1','0','F','0','0','system:dict:export','#'),
    ('F:system:dict:query','C:system/dict/index','字典管理 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:dict:query','#'),
    ('F:system:dict:remove','C:system/dict/index','字典管理 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:dict:remove','#'),
    ('F:system:factory:add','C:system/factory/index','工厂管理新增','0','',NULL,NULL,'','1','0','F','0','0','system:factory:add','#'),
    ('F:system:factory:edit','C:system/factory/index','工厂管理编辑','0','',NULL,NULL,'','1','0','F','0','0','system:factory:edit','#'),
    ('F:system:factory:query','C:system/factory/index','工厂管理查询','0','',NULL,NULL,'','1','0','F','0','0','system:factory:query','#'),
    ('F:system:factory:remove','C:system/factory/index','工厂管理删除','0','',NULL,NULL,'','1','0','F','0','0','system:factory:remove','#'),
    ('F:system:menu:add','C:system/menu/index','菜单管理 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:menu:add','#'),
    ('F:system:menu:edit','C:system/menu/index','菜单管理 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:menu:edit','#'),
    ('F:system:menu:query','C:system/menu/index','菜单管理 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:menu:query','#'),
    ('F:system:menu:remove','C:system/menu/index','菜单管理 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:menu:remove','#'),
    ('F:system:message:add','C:system/message/index','消息add','2','',NULL,NULL,'','1','0','F','0','0','system:message:add','#'),
    ('F:system:message:edit','C:system/message/index','消息edit','3','',NULL,NULL,'','1','0','F','0','0','system:message:edit','#'),
    ('F:system:message:export','C:system/message/index','消息export','5','',NULL,NULL,'','1','0','F','0','0','system:message:export','#'),
    ('F:system:message:query','C:system/message/index','消息query','1','',NULL,NULL,'','1','0','F','0','0','system:message:query','#'),
    ('F:system:message:remove','C:system/message/index','消息remove','4','',NULL,NULL,'','1','0','F','0','0','system:message:remove','#'),
    ('F:system:notice:add','C:system/notice/index','通知公告 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:notice:add','#'),
    ('F:system:notice:edit','C:system/notice/index','通知公告 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:notice:edit','#'),
    ('F:system:notice:query','C:system/notice/index','通知公告 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:notice:query','#'),
    ('F:system:notice:remove','C:system/notice/index','通知公告 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:notice:remove','#'),
    ('F:system:openApiClient:add','C:system/openApiClient/index','新建应用','2','#',NULL,NULL,'','1','0','F','0','0','system:openApiClient:add','#'),
    ('F:system:openApiClient:authorize','C:system/openApiClient/index','接口授权','5','#',NULL,NULL,'','1','0','F','0','0','system:openApiClient:authorize','#'),
    ('F:system:openApiClient:edit','C:system/openApiClient/index','应用状态','3','#',NULL,NULL,'','1','0','F','0','0','system:openApiClient:edit','#'),
    ('F:system:openApiClient:query','C:system/openApiClient/index','应用查询','1','#',NULL,NULL,'','1','0','F','0','0','system:openApiClient:query','#'),
    ('F:system:openApiClient:reset','C:system/openApiClient/index','密钥重置','4','#',NULL,NULL,'','1','0','F','0','0','system:openApiClient:reset','#'),
    ('F:system:post:add','C:system/post/index','岗位管理 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:post:add','#'),
    ('F:system:post:edit','C:system/post/index','岗位管理 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:post:edit','#'),
    ('F:system:post:export','C:system/post/index','岗位管理 · 导出','0','',NULL,NULL,'','1','0','F','0','0','system:post:export','#'),
    ('F:system:post:query','C:system/post/index','岗位管理 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:post:query','#'),
    ('F:system:post:remove','C:system/post/index','岗位管理 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:post:remove','#'),
    ('F:system:role:add','C:system/role/index','角色管理 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:role:add','#'),
    ('F:system:role:edit','C:system/role/index','角色管理 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:role:edit','#'),
    ('F:system:role:export','C:system/role/index','角色管理 · 导出','0','',NULL,NULL,'','1','0','F','0','0','system:role:export','#'),
    ('F:system:role:query','C:system/role/index','角色管理 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:role:query','#'),
    ('F:system:role:remove','C:system/role/index','角色管理 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:role:remove','#'),
    ('F:system:user:add','C:system/user/index','用户管理 · 新增','0','',NULL,NULL,'','1','0','F','0','0','system:user:add','#'),
    ('F:system:user:edit','C:system/user/index','用户管理 · 修改','0','',NULL,NULL,'','1','0','F','0','0','system:user:edit','#'),
    ('F:system:user:export','C:system/user/index','用户管理 · 导出','0','',NULL,NULL,'','1','0','F','0','0','system:user:export','#'),
    ('F:system:user:import','C:system/user/index','用户管理 · 导入','0','',NULL,NULL,'','1','0','F','0','0','system:user:import','#'),
    ('F:system:user:query','C:system/user/index','用户管理 · 查询','0','',NULL,NULL,'','1','0','F','0','0','system:user:query','#'),
    ('F:system:user:remove','C:system/user/index','用户管理 · 删除','0','',NULL,NULL,'','1','0','F','0','0','system:user:remove','#'),
    ('F:system:user:resetPwd','C:system/user/index','用户管理 · 重置密码','0','',NULL,NULL,'','1','0','F','0','0','system:user:resetPwd','#'),
    ('F:workorder:create','C:production/workorder/index','工单新增','2','#',NULL,NULL,'','1','0','F','0','0','workorder:create','#'),
    ('F:workorder:delete','C:production/workorder/index','工单删除','4','#',NULL,NULL,'','1','0','F','0','0','workorder:delete','#'),
    ('F:workorder:list','C:production/workorder/index','工单列表','0','#',NULL,NULL,'','1','0','F','0','0','workorder:list','#'),
    ('F:workorder:material:update','C:production/workorder/index','本地工单用料维护','40','#',NULL,NULL,'','1','1','F','0','0','workorder:material:update','#'),
    ('F:workorder:query','C:production/workorder/index','工单详情','1','#',NULL,NULL,'','1','0','F','0','0','workorder:query','#'),
    ('F:workorder:release','C:production/workorder/index','下达工单','20','#',NULL,NULL,'','1','0','F','0','0','workorder:release','#'),
    ('F:workorder:sync','C:production/workorder/index','ERP工单同步准备','49','#',NULL,NULL,'','1','1','F','0','0','workorder:sync','#'),
    ('F:workorder:update','C:production/workorder/index','工单编辑','3','#',NULL,NULL,'','1','0','F','0','0','workorder:update','#'),
    ('M:basic',NULL,'基础数据','0','basic',NULL,NULL,'','1','0','M','0','0',NULL,'list'),
    ('M:issue',NULL,'领料出库','1','issue',NULL,NULL,'','1','0','M','0','0',NULL,'list'),
    ('M:monitor',NULL,'系统监控','1','monitor',NULL,NULL,'','1','0','M','0','0',NULL,'#'),
    ('M:production',NULL,'生产管理','20','production',NULL,NULL,'','1','0','M','0','0',NULL,'list'),
    ('M:quality',NULL,'质量管理','40','quality',NULL,NULL,'','1','1','M','0','0','','list'),
    ('M:sales',NULL,'销售管理','1','sales',NULL,NULL,'','1','0','M','0','0',NULL,'ShoppingCart'),
    ('M:stock',NULL,'物料库存','1','stock',NULL,NULL,'','1','0','M','0','0',NULL,'list'),
    ('M:system',NULL,'系统管理','1','system',NULL,NULL,'','1','0','M','0','0',NULL,'#'),
    ('M:tool',NULL,'系统工具','3','tool',NULL,NULL,'','1','0','M','0','0',NULL,'#');
    IF EXISTS (
        SELECT d.menu_key FROM mes_v47_definitions d JOIN sys_menu m
          ON BINARY d.menu_key=BINARY (CASE m.menu_type WHEN 'M' THEN CONCAT('M:',m.path) WHEN 'C' THEN CONCAT('C:',m.component) WHEN 'F' THEN CONCAT('F:',m.perms) END)
        GROUP BY d.menu_key HAVING COUNT(*)<>1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V47 duplicate semantic menu identity; stop before menu changes';
    END IF;
    IF EXISTS (
        SELECT 1 FROM mes_v47_definitions d JOIN sys_menu m
          ON BINARY d.menu_key=BINARY (CASE m.menu_type WHEN 'M' THEN CONCAT('M:',m.path) WHEN 'C' THEN CONCAT('C:',m.component) WHEN 'F' THEN CONCAT('F:',m.perms) END)
        WHERE NOT(CAST(m.perms AS BINARY) <=> CAST(d.perms AS BINARY))
          AND NOT(m.menu_type='C' AND m.component='basic/materials/index'
            AND m.perms='business:material:list' AND d.perms IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V47 unexpected permission definition drift; stop';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sys_role_menu rm JOIN sys_menu page ON page.menu_id=rm.menu_id
        WHERE page.menu_type='C' AND page.component='basic/materials/index'
          AND page.perms='business:material:list'
          AND NOT EXISTS (SELECT 1 FROM sys_role_menu carried JOIN sys_menu button ON button.menu_id=carried.menu_id
            WHERE carried.role_id=rm.role_id AND button.menu_type='F'
              AND button.perms='business:material:list' AND button.status='0')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V47 material list permission carrier must be preserved through official authorization first';
    END IF;
    START TRANSACTION;
    INSERT INTO mes_v47_previous_permissions(role_id,perms)
      SELECT DISTINCT rm.role_id,m.perms FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
      WHERE m.status='0' AND m.perms IS NOT NULL AND m.perms<>'';
    INSERT INTO mes_v47_bindings(menu_key,menu_id)
      SELECT d.menu_key,m.menu_id FROM mes_v47_definitions d JOIN sys_menu m
        ON BINARY d.menu_key=BINARY (CASE m.menu_type WHEN 'M' THEN CONCAT('M:',m.path) WHEN 'C' THEN CONCAT('C:',m.component) WHEN 'F' THEN CONCAT('F:',m.perms) END);
    SELECT COUNT(*) INTO expected_count FROM mes_v47_definitions;
    SELECT COUNT(*) INTO resolved_count FROM mes_v47_bindings;
    WHILE resolved_count < expected_count DO
        DELETE FROM mes_v47_parents;
        INSERT INTO mes_v47_parents SELECT menu_key,menu_id FROM mes_v47_bindings;
        INSERT INTO sys_menu (`menu_name`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,parent_id,create_by,create_time)
          SELECT d.`menu_name`,d.`order_num`,d.`path`,d.`component`,d.`query`,d.`route_name`,d.`is_frame`,d.`is_cache`,d.`menu_type`,d.`visible`,d.`status`,d.`perms`,d.`icon`,COALESCE(p.menu_id,0),'migration',CURRENT_TIMESTAMP
          FROM mes_v47_definitions d
          LEFT JOIN mes_v47_bindings b ON b.menu_key=d.menu_key
          LEFT JOIN mes_v47_parents p ON p.menu_key=d.parent_key
          WHERE b.menu_id IS NULL AND (d.parent_key IS NULL OR p.menu_id IS NOT NULL);
        SET inserted_count=ROW_COUNT();
        IF inserted_count=0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V47 unresolved semantic parent; rollback menu changes';
        END IF;
        INSERT INTO mes_v47_bindings(menu_key,menu_id)
          SELECT d.menu_key,m.menu_id FROM mes_v47_definitions d JOIN sys_menu m
            ON BINARY d.menu_key=BINARY (CASE m.menu_type WHEN 'M' THEN CONCAT('M:',m.path) WHEN 'C' THEN CONCAT('C:',m.component) WHEN 'F' THEN CONCAT('F:',m.perms) END)
          LEFT JOIN mes_v47_parents old ON old.menu_key=d.menu_key
          WHERE old.menu_id IS NULL;
        SELECT COUNT(*) INTO resolved_count FROM mes_v47_bindings;
    END WHILE;
    DELETE FROM mes_v47_parents;
    INSERT INTO mes_v47_parents SELECT menu_key,menu_id FROM mes_v47_bindings;
    UPDATE sys_menu m JOIN mes_v47_bindings b ON b.menu_id=m.menu_id
      JOIN mes_v47_definitions d ON d.menu_key=b.menu_key
      LEFT JOIN mes_v47_parents p ON p.menu_key=d.parent_key
      SET m.`menu_name`=d.`menu_name`,
          m.`order_num`=d.`order_num`,
          m.`path`=d.`path`,
          m.`component`=d.`component`,
          m.`query`=d.`query`,
          m.`route_name`=d.`route_name`,
          m.`is_frame`=d.`is_frame`,
          m.`is_cache`=d.`is_cache`,
          m.`menu_type`=d.`menu_type`,
          m.`visible`=d.`visible`,
          m.`status`=d.`status`,
          m.`perms`=d.`perms`,
          m.`icon`=d.`icon`,
          m.parent_id=COALESCE(p.menu_id,0),
          m.update_by='migration',
          m.update_time=CURRENT_TIMESTAMP
      WHERE NOT(CAST(m.`menu_name` AS BINARY) <=> CAST(d.`menu_name` AS BINARY) AND CAST(m.`order_num` AS BINARY) <=> CAST(d.`order_num` AS BINARY) AND CAST(m.`path` AS BINARY) <=> CAST(d.`path` AS BINARY) AND CAST(m.`component` AS BINARY) <=> CAST(d.`component` AS BINARY) AND CAST(m.`query` AS BINARY) <=> CAST(d.`query` AS BINARY) AND CAST(m.`route_name` AS BINARY) <=> CAST(d.`route_name` AS BINARY) AND CAST(m.`is_frame` AS BINARY) <=> CAST(d.`is_frame` AS BINARY) AND CAST(m.`is_cache` AS BINARY) <=> CAST(d.`is_cache` AS BINARY) AND CAST(m.`menu_type` AS BINARY) <=> CAST(d.`menu_type` AS BINARY) AND CAST(m.`visible` AS BINARY) <=> CAST(d.`visible` AS BINARY) AND CAST(m.`status` AS BINARY) <=> CAST(d.`status` AS BINARY) AND CAST(m.`perms` AS BINARY) <=> CAST(d.`perms` AS BINARY) AND CAST(m.`icon` AS BINARY) <=> CAST(d.`icon` AS BINARY) AND m.parent_id <=> COALESCE(p.menu_id,0));
    UPDATE sys_menu SET status='1',visible='1',update_by='migration',update_time=CURRENT_TIMESTAMP
      WHERE ((menu_type='F' AND perms='business:mes:production:execution:create') OR (menu_type='F' AND perms='business:production:receipt:reverse') OR (menu_type='F' AND perms='business:production:receipt:create') OR (menu_type='C' AND component='acceptance/person/index') OR (menu_type='F' AND perms='business:person:read') OR (menu_type='F' AND perms='business:person:create') OR (menu_type='F' AND perms='business:person:update') OR (menu_type='F' AND perms='business:person:delete') OR (menu_type='F' AND perms='business:pro:workorder:read')) AND (status<>'1' OR visible<>'1');
    -- 旧目录只有在不再承载任何有效子节点时才禁用，不压住合法子页。
    UPDATE sys_menu parent LEFT JOIN sys_menu child ON child.parent_id=parent.menu_id AND child.status='0'
      SET parent.status='1',parent.visible='1',parent.update_by='migration',parent.update_time=CURRENT_TIMESTAMP
      WHERE parent.menu_type='M' AND parent.path IN ('acceptance','pro') AND child.menu_id IS NULL
        AND (parent.status<>'1' OR parent.visible<>'1');
    IF EXISTS (
        SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
        WHERE m.status='0' AND m.perms IS NOT NULL AND m.perms<>''
          AND NOT EXISTS(SELECT 1 FROM mes_v47_previous_permissions old WHERE old.role_id=rm.role_id AND BINARY old.perms=BINARY m.perms)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V47 would grant a new role permission; rollback';
    END IF;
    IF EXISTS (
        SELECT 1 FROM mes_v47_previous_permissions old WHERE old.perms NOT IN ('business:mes:production:execution:create','business:person:create','business:person:delete','business:person:read','business:person:update','business:pro:workorder:read','business:production:receipt:create','business:production:receipt:reverse')
          AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
            WHERE rm.role_id=old.role_id AND m.status='0' AND BINARY m.perms=BINARY old.perms)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V47 would remove an unrelated role permission; rollback';
    END IF;
    DROP TEMPORARY TABLE mes_v47_previous_permissions;
    DROP TEMPORARY TABLE mes_v47_parents;
    DROP TEMPORARY TABLE mes_v47_bindings;
    DROP TEMPORARY TABLE mes_v47_definitions;
    COMMIT;
END$$
CALL mes_v47_align_current_navigation()$$
DROP PROCEDURE mes_v47_align_current_navigation$$
DELIMITER ;
