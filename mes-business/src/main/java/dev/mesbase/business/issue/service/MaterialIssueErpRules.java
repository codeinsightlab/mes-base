package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Minimal verified transitions. A conflicting external state never directly replaces a MES state.
 */
final class MaterialIssueErpRules {
    private MaterialIssueErpRules() {
    }

    static String reconcile(MesMaterialIssueEntity row, MesMaterialIssueErpExtraEntity extra,
                            ErpIssueQueryResult result) {
        String message = result.message();
        if (result.executionMode() == Mode.CONNECTED && result.outcome() == Outcome.FOUND) {
            extra.setExternalReference(result.externalId());
            extra.setExternalCode(result.externalCode());
            extra.setDocumentStatus(result.documentStatus().name());
            if (result.documentStatus() == DocumentStatus.CANCELLED && Set.of("VOID_PENDING", "VOID_UNKNOWN")
                    .contains(row.getStatus()) && row.getVoidOperationId() != null) {
                row.setStatus("VOIDED");
                row.setVoidFinishedAt(LocalDateTime.now());
                extra.setVoidResultStatus("SUCCEEDED");
                extra.setVoidExecutionMode("CONNECTED");
                extra.setExternalEffect("REVERSED");
            } else if (result.documentStatus() == DocumentStatus.EFFECTIVE && Set.of("ERP_PENDING", "ERP_UNKNOWN")
                    .contains(row.getStatus())) {
                row.setStatus("RECORDED");
                extra.setExternalEffect("CONFIRMED");
            } else if (result.documentStatus() == DocumentStatus.EFFECTIVE && "VOIDED".equals(row.getStatus())
                    || result.documentStatus() == DocumentStatus.CANCELLED && !"VOIDED".equals(row.getStatus())) {
                message = "ERP与MES状态冲突，保持MES状态，需项目级人工处理";
                extra.setExternalEffect("UNKNOWN");
            }
        }
        return message;
    }
}
