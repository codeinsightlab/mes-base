package dev.mesbase.business.wm.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business purpose is distinct from the receipt/issue direction.
 */
public enum StockDocumentType {
    PURCHASE_RECEIPT("采购入库", "供应商交货", "采购订单、收货单", false),
    PRODUCTION_RECEIPT("生产完工入库", "工单生产完成", "工单、Execution、报工及质量放行记录", true),
    OUTSOURCE_RECEIPT("委外加工入库", "外协厂加工完成", "委外单、委外发料记录", false),
    MATERIAL_RETURN_RECEIPT("退料入库", "生产领料后退回", "原领料单、工单", false),
    REWORK_RECEIPT("返工入库", "返工完成并检验合格", "原不良记录、返工任务及检验依据", false),
    SALES_RETURN_RECEIPT("销售退货入库", "客户退回产品", "销售出库单、退货单", false),
    DEFECT_RECEIPT("不良品入库", "不良品转入不良品仓", "原不良记录、处置流水", true),
    SCRAP_RECEIPT("报废品入库", "不良品转入报废品仓", "原不良记录、处置流水", true),
    OTHER_RECEIPT("其他入库", "盘盈、期初等特殊业务", "原因及实物、审批或调整依据", true),
    OTHER_ISSUE("其他出库", "其他库存发出", "原因及实物、审批或调整依据", true);

    private final String description;
    private final String origin;
    private final String requiredDocuments;
    private final boolean approvalAvailable;

    StockDocumentType(String description, String origin, String requiredDocuments, boolean approvalAvailable) {
        this.description = description;
        this.origin = origin;
        this.requiredDocuments = requiredDocuments;
        this.approvalAvailable = approvalAvailable;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return name();
    }

    public String getOrigin() {
        return origin;
    }

    public String getRequiredDocuments() {
        return requiredDocuments;
    }

    public boolean isApprovalAvailable() {
        return approvalAvailable;
    }

    public boolean isReceipt() {
        return this != OTHER_ISSUE;
    }

    public boolean isQualityReceipt() {
        return this == DEFECT_RECEIPT || this == SCRAP_RECEIPT;
    }

    public static StockDocumentType from(String value) {
        for (var item : values()) if (item.getValue().equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        var result = new LinkedHashMap<String, String>();
        for (var item : values()) result.put(item.getValue(), item.description);
        return result;
    }
}
