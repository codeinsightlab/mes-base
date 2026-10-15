package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.integration.warehouse.ExternalEffect;
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
            extra.setDocumentStatus(result.documentStatus().getValue());
            if (result.documentStatus() == DocumentStatus.CANCELLED && Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                    .contains(row.getStatus()) && row.getVoidOperationId() != null) {
                row.setStatus(MaterialIssueStatus.VOIDED.getValue());
                row.setVoidFinishedAt(LocalDateTime.now());
                extra.setVoidResultStatus(Status.SUCCEEDED.getValue());
                extra.setVoidExecutionMode(Mode.CONNECTED.getValue());
                extra.setExternalEffect(ExternalEffect.REVERSED.getValue());
            } else if (result.documentStatus() == DocumentStatus.EFFECTIVE && Set.of(MaterialIssueStatus.ERP_PENDING.getValue(), MaterialIssueStatus.ERP_UNKNOWN.getValue())
                    .contains(row.getStatus())) {
                row.setStatus(MaterialIssueStatus.RECORDED.getValue());
                extra.setExternalEffect(ExternalEffect.CONFIRMED.getValue());
            } else if (result.documentStatus() == DocumentStatus.EFFECTIVE && MaterialIssueStatus.VOIDED.getValue()
                    .equals(row.getStatus())
                    || result.documentStatus() == DocumentStatus.CANCELLED && !MaterialIssueStatus.VOIDED.getValue()
                    .equals(row.getStatus())) {
                message = "ERP与MES状态冲突，保持MES状态，需项目级人工处理";
                extra.setExternalEffect(ExternalEffect.UNKNOWN.getValue());
            }
        }
        return message;
    }
}
