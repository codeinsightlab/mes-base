package dev.mesbase.core.error;


public enum ErrorCode {
    STOCK_DOCUMENT_SOURCE_PENDING(422, "来源业务尚未接通或关联依据不完整，禁止审核"),
    RECEIPT_QUALITY_UNVERIFIED(409, "缺少当前质量依据下的正式放行或质量核验未通过，禁止新增生产交接"),
    RECEIPT_PATH_REQUIRED(409, "交接需要已锁定的确切实际路径及末道有效产出"),
    RECEIPT_WAREHOUSE_INVALID(422, "目标仓库不存在或未启用"),
    RECEIPT_REFERENCE_INVALID(422, "交接冲销只能引用原始正向交接"),
    RECEIPT_QUANTITY_CONFLICT(409, "交接超出末道有效产出额度，或冲销超过原始剩余余额"),
    RECEIPT_IDEMPOTENCY_CONFLICT(409, "请求UUID已用于其他交接动作、参数或操作人"),
    EXECUTION_QUALITY_REFERENCED(409, "该生产执行已存在质量申报历史，为保证生产追溯，不允许删除或重新选择实际路径"),
    QUALITY_DATA_INVALID(409, "质量记录不符合当前流程，请由数据导入流程核对转换"),
    QUALITY_TREATMENT_CONFLICT(409, "质量处置数量、版本或归属冲突，请刷新后核实"),
    QUALITY_ATTRIBUTION_REQUIRED(409, "不良归属待核实，暂不能据此继续报工或交接"),
    DEFECT_TYPE_DUPLICATE(409, "同工序已有相同不良类型编码"),
    DEFECT_TYPE_STALE(409, "不良类型配置已变化，请刷新后重新确认"),
    DEFECT_TYPE_INVALID(422, "不良类型未启用或不属于当前实际工序"),
    DEFECT_TYPE_REFERENCED(409, "不良类型已被历史申报引用，不能删除"),
    DEFECT_IDEMPOTENCY_CONFLICT(409, "不良申报请求标识已用于不同命令"),
    DEFECT_REVISION_CONFLICT(409, "不良申报已订正或作废，请刷新后重试"),
    REPORT_PATH_REQUIRED(409, "报工必须引用执行已锁定的完整实际路径"),
    REPORT_QUANTITY_CONFLICT(409, "数量变更导致超量、负余额或上下游数量倒挂"),
    REPORT_REFERENCE_INVALID(422, "报工人员、工作站或原始报工引用无效"),
    REPORT_ORIGINAL_EMPTY(409, "原始报工有效余额已为零，不能重复冲销"),
    OPERATOR_NOT_AUTHORIZED(422, "用户不存在、已停用或无当前工厂业务授权"),
    OPERATOR_CAPABILITY_REQUIRED(422, "操作人员不具备当前工序能力"),
    OPERATOR_NOT_ASSIGNED(422, "当前节点已指定派工人员，该用户不在名单中"),
    ASSIGNMENT_NODE_INVALID(422, "节点不属于执行单元的确切工艺版本"),
    ASSIGNMENT_EXECUTION_CLOSED(409, "当前执行状态不允许维护派工"),
    FACTORY_MODE_CHANGE_REQUIRES_MIGRATION(409, "该工厂已锁定业务运行配置，切换模式或ERP绑定需要专门迁移流程"),
    BUSINESS_SOURCE_CONFLICT(409, "业务来源缺失、不匹配或当前模式不允许"),
    EXTERNAL_SOURCE_READ_ONLY(409, "外部权威数据只能由受控同步维护"),
    INTEGRATION_NOT_CONFIGURED(422, "当前工厂仓储模式或对应领域连接未配置"),
    ISSUE_EXTERNAL_UNVERIFIED(409, "单据外部出库来源未核实，不能自动本地作废"),
    ISSUE_VOID_CONFLICT(409, "领料单已作废或正在核实，不允许普通编辑或继续使用"),
    WORKORDER_ISSUE_REFERENCED(409, "工单存在有效或外部结果待核实的领料单，不能更换产品"),
    EXECUTION_INVALID(422, "生产执行必须关联有效工单、匹配产品和已发布工艺，并具有正计划数量及有效时间"),
    EXECUTION_FROZEN(409, "执行身份或已有生产事实关联不可更改"),
    EXECUTION_REFERENCED(409, "生产执行已启动或存在领料关联，不能删除"),
    PRODUCTION_BATCH_REQUIRED(409, "缺少可信生产批次，无法入库，请核对投产身份"),
    PRODUCTION_BATCH_CONFLICT(409, "生产批次绑定不一致，无法入库，请核对投产身份"),
    PRODUCTION_IDENTITY_AMBIGUOUS(409, "材料批次关联多个产出产品，请扫描生产身份码或选择明确执行"),
    PRODUCTION_INPUT_STALE(409, "用料计算依据已变化，请重新计算并确认"),
    PRODUCTION_SINGLE_MATERIAL_REQUIRED(422, "工单包含多种必需材料，不支持当前单材料投产模式"),
    PRODUCTION_IDENTITY_CONFLICT(409, "生产扫码身份已绑定其他工单"),
    PRODUCTION_SUPPLEMENT_UNSUPPORTED(422, "当前版本尚不支持纯材料补料提交"),
    EXECUTION_QR_INVALID(400, "扫码格式应为EXE:执行主键或版本化MES1生产身份"),
    EXECUTION_NOT_FOUND(404, "当前工厂未找到该生产执行身份"),
    WORKORDER_INVALID(422, "工单产品、计划数量、默认工艺或计划日期不符合规则"),
    WORKORDER_NOT_RELEASED(422, "工单尚未下达，只能预建待执行身份，不能开工或结束生产"),
    WORKORDER_LEGACY_REFERENCED(409, "工单含旧批次历史，不能删除或更换产品及默认工艺"),
    LEGACY_BATCH_READ_ONLY(410, "旧生产批次和路径仅供历史查询，分批生产请使用生产执行单元"),
    ROUTING_GRAPH_INVALID(422, "路线拓扑无效：请检查节点、连接、环路及孤立节点"),
    STOCK_REFERENCE_INVALID(422, "物料和仓库必须存在且属于当前工厂"),
    WAREHOUSE_REFERENCED(409, "仓库已被库存引用，不能删除"),
    MATERIAL_INVENTORY_REFERENCED(409, "物料已被库存引用，不能删除"),
    STOCK_DOCUMENT_CONFLICT(409, "单据状态或版本已变化，或明细库存身份不一致，请刷新核对"),
    STOCK_DOCUMENT_REQUIRED(409, "入库请通过入库单确认过账"),
    STOCK_DOCUMENT_BASIS_INVALID(409, "无法证明可补入库数量，或数量超过原交接未接收净量"),
    STOCK_INSUFFICIENT(409, "所选库存来源的实存或可用数量不足，整张单据未过账"),
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
    REPORT_REQUEST_ID_REQUIRED(400, "管理员报工命令必须携带稳定的requestId UUID，请升级客户端后重试"),
    REPORT_REQUEST_CONFLICT(409, "报工请求标识已被不同命令或操作者使用"),
    PRODUCTION_CLOSURE_BLOCKED(409, "操作条件尚未满足，请核对生产数量、质量或当前仓储操作依据"),
    PRODUCTION_CLOSURE_CONFLICT(409, "闭环请求、版本或业务引用冲突，请使用原请求核实"),
    WORKORDER_CANCELLED(409, "所属工单已作废，禁止新增正向生产业务；历史事实保留"),
    WORKORDER_CANCELLATION_CONFLICT(409, "工单作废请求、版本或状态冲突，请核实原请求"),
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
