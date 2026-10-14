package dev.mesbase.business.integration.warehouse;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;

public record ErpIssueQueryResult(Outcome outcome, DocumentStatus documentStatus, Mode executionMode,
                                  String externalId, String externalCode, String message) {
    public enum Outcome {FOUND, NOT_FOUND, UNKNOWN}

    public enum DocumentStatus {DRAFT, PENDING_APPROVAL, EFFECTIVE, CANCELLED, REJECTED, UNKNOWN}

    public static ErpIssueQueryResult local() {
        return new ErpIssueQueryResult(Outcome.NOT_FOUND, DocumentStatus.UNKNOWN, Mode.LOCAL_ONLY, null, null, "本地模式，没有ERP单据或审核事实");
    }

    public static ErpIssueQueryResult unknown(Mode mode) {
        return new ErpIssueQueryResult(Outcome.UNKNOWN, DocumentStatus.UNKNOWN, mode, null, null, "ERP查询结果不确定，保持本地状态，请人工核实");
    }
}
