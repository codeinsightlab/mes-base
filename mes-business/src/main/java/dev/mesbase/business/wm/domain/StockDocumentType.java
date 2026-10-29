package dev.mesbase.business.wm.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business purpose is distinct from the receipt/issue direction.
 */
public enum StockDocumentType {
    PURCHASE_RECEIPT("PURCHASE_RECEIPT", StockDocumentDirection.IN, "采购入库", "供应商交货", "采购订单、收货单", false),
    PRODUCTION_RECEIPT("PRODUCTION_RECEIPT", StockDocumentDirection.IN, "生产完工入库", "工单生产完成", "工单、Execution、有效报工及质量数量依据", true),
    OUTSOURCE_RECEIPT("OUTSOURCE_RECEIPT", StockDocumentDirection.IN, "委外加工入库", "外协厂加工完成", "委外单、委外发料记录", false),
    MATERIAL_RETURN_RECEIPT("MATERIAL_RETURN_RECEIPT", StockDocumentDirection.IN, "退料入库", "生产领料后退回", "原领料单、工单", false),
    REWORK_RECEIPT("REWORK_RECEIPT", StockDocumentDirection.IN, "返工入库", "返工完成并检验合格", "原不良记录、返工任务及检验依据", false),
    SALES_RETURN_RECEIPT("SALES_RETURN_RECEIPT", StockDocumentDirection.IN, "销售退货入库", "客户退回产品", "销售出库单、退货单", false),
    DEFECT_RECEIPT("DEFECT_RECEIPT", StockDocumentDirection.IN, "不良品入库", "不良品转入不良品仓", "原不良记录、处置流水", true),
    SCRAP_RECEIPT("SCRAP_RECEIPT", StockDocumentDirection.IN, "报废品入库", "不良品转入报废品仓", "原不良记录、处置流水", true),
    OTHER_RECEIPT("OTHER_RECEIPT", StockDocumentDirection.IN, "其他入库", "盘盈、期初等特殊业务", "原因及实物、审批或调整依据", true),
    MATERIAL_ISSUE("MATERIAL_ISSUE", StockDocumentDirection.OUT, "生产领料出库", "工单生产投入", "已发布BOM、生产领料意图、实际路径", true),
    TRANSFER_RECEIPT("TRANSFER_RECEIPT", StockDocumentDirection.IN, "调拨入库", "本厂LOCAL库存调拨", "关联调拨出库单", true),
    TRANSFER_ISSUE("TRANSFER_ISSUE", StockDocumentDirection.OUT, "调拨出库", "本厂LOCAL库存调拨", "关联调拨入库单", true),
    OTHER_ISSUE("OTHER_ISSUE", StockDocumentDirection.OUT, "其他出库", "其他库存发出", "原因及实物、审批或调整依据", true);

    private final String value;
    private final StockDocumentDirection direction;
    private final String description;
    private final String origin;
    private final String requiredDocuments;
    private final boolean approvalAvailable;

    StockDocumentType(String value, StockDocumentDirection direction, String description, String origin,
                      String requiredDocuments,
                      boolean approvalAvailable) {
        this.value = value;
        this.direction = direction;
        this.description = description;
        this.origin = origin;
        this.requiredDocuments = requiredDocuments;
        this.approvalAvailable = approvalAvailable;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public StockDocumentDirection getDirection() {
        return direction;
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

    public boolean isTransfer() {
        return this == TRANSFER_RECEIPT || this == TRANSFER_ISSUE;
    }

    public boolean isReceipt() {
        return direction == StockDocumentDirection.IN;
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
