package dev.mesbase.core.error;

public enum ErrorCode {
    EXECUTION_INVALID(422, "生产执行必须关联有效工单、匹配产品和已发布工艺，并具有正计划数量及有效时间"),
    EXECUTION_FROZEN(409, "执行身份或已有生产事实关联不可更改"),
    EXECUTION_REFERENCED(409, "生产执行已启动或存在领料关联，不能删除"),
    EXECUTION_QR_INVALID(400, "执行身份码格式应为EXE:正整数主键"),
    EXECUTION_NOT_FOUND(404, "当前工厂未找到该生产执行身份"),
    WORKORDER_INVALID(422, "工单、批次数量、工艺引用或完整路径不符合规则"),
    BATCH_ROUTE_FROZEN(409, "路径已锁定、正在出库或结果待核实，禁止更改"),
    ROUTING_GRAPH_INVALID(422, "路线拓扑无效：请检查节点、连接、环路及孤立节点"),
    STOCK_REFERENCE_INVALID(422, "物料和仓库必须存在且属于当前工厂"),
    WAREHOUSE_REFERENCED(409, "仓库已被库存引用，不能删除"),
    MATERIAL_INVENTORY_REFERENCED(409, "物料已被库存引用，不能删除"),
    STOCK_KEY_DUPLICATE(409, "当前工厂已有相同仓库编码或物料仓库库存记录"),
    ROUTING_FROZEN(409, "已发布路线内容已冻结，请创建新版本"),
    ROUTING_REVISION_CONFLICT(409, "记录已被修改，请刷新后重试"),
    ROUTING_REFERENCE_INVALID(422, "路线引用必须属于当前工厂且处于启用状态"),
    ROUTING_PUBLISH_INCOMPLETE(422, "发布前请至少关联一个物料产品和一个工序步骤"),
    OPERATION_REFERENCED(409, "工序已被工艺路线引用，不能删除"),
    MATERIAL_ROUTING_REFERENCED(409, "物料产品已被工艺路线引用，不能删除"),
    ERP_ROUTING_NOT_CONFIGURED(422, "暂未配置工艺路线 ERP 同步数据来源"),
    ERP_OPERATION_NOT_CONFIGURED(422, "暂未配置工序 ERP 同步数据来源"),
    SYNC_QUERY_NOT_IMPLEMENTED(422, "暂未接入外部同步查询接口"),
    ERP_SYNC_NOT_CONFIGURED(422, "暂未接入 ERP 物料查询接口"),
    SYNC_RUNNING(409, "同步正在进行"), SYNC_EXECUTION_LOST(409, "同步执行权已失效"),
    RESOURCE_CONFLICT(409, "Resource conflicts with current state"), BAD_REQUEST(400, "Invalid request"), UNAUTHENTICATED(401, "Authentication required"),
    FORBIDDEN(403, "Permission denied"), NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Execution binding changed"), UNPROCESSABLE(422, "Request cannot be processed"),
    FACTORY_SELECTION_REQUIRED(422, "当前工厂不可用，请重新选择工厂"),
    FACTORY_SCOPE_REQUIRED(400, "Select a factory explicitly"), FACTORY_NOT_FOUND(404, "Factory not found"),
    FACTORY_DISABLED(422, "Factory is disabled"), RUNTIME_NOT_CONFIGURED(422, "Business runtime is not configured"),
    ROUTING_METADATA_UNAVAILABLE(503, "路由元数据暂时不可用，请稍后重试"),
    AUTHENTICATION_UNAVAILABLE(503, "认证服务暂时不可用，请稍后重试"),
    SESSION_REVOCATION_PENDING(503, "账号变更已保存，会话撤销待重试；旧会话准入已拒绝"),
    EXECUTION_UNKNOWN(503, "Database saved; scheduler result UNKNOWN. Retry synchronization or check task logs"), INTERNAL_ERROR(500, "Internal server error");
    private final int httpStatus;
    private final String message;

    ErrorCode(int status, String message) {
        this.httpStatus = status;
        this.message = message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String safeMessage() {
        return message;
    }
}
