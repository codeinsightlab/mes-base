package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LOCAL production-input reversal, stored on the production-input fact and unified stock document.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueVoidService {
    private final StockDocumentService documents;
    private final MaterialIssueInputFacts inputFacts;
    private final ProductionExecutionRules executions;
    private final ProductionQuantityService quantities;

    public MaterialIssueVoidService(StockDocumentService documents, MaterialIssueInputFacts inputFacts,
                                    ProductionExecutionRules executions, ProductionQuantityService quantities) {
        this.documents = documents;
        this.inputFacts = inputFacts;
        this.executions = executions;
        this.quantities = quantities;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    @BusinessCacheEvict(business = "business:wm:stock:document", scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public Result cancel(Long documentId) {
        return perform(documentId, false);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    @BusinessCacheEvict(business = "business:wm:stock:document", scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public Result reconcile(Long documentId) {
        return perform(documentId, true);
    }

    private Result perform(Long documentId, boolean recovering) {
        var detail = documents.detail(documentId);
        var document = detail.document();
        if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(document.getDocumentType()))
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var input = inputFacts.find(documentId);
        if (input == null) {
            if (recovering || document.getSourceRequestJson() == null)
                throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
            if (!StockDocumentStatus.DRAFT.getValue().equals(document.getStatus()))
                throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
            documents.cancel(documentId, new StockDocumentModels.Version(document.getRevision()));
            return result(documentId, StockDocumentStatus.CANCELLED.getValue(), null);
        }
        if (!Mode.LOCAL_ONLY.getValue().equals(input.getSourceMode()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        Long executionId = input.getExecutionId();
        executions.lock(executionId);
        document = documents.lockMaterialIssueForVoid(documentId);
        input = inputFacts.find(documentId);
        if (input == null || !Objects.equals(executionId, input.getExecutionId())
                || !Objects.equals(document.getSourceExecutionId(), input.getExecutionId()))
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        var reversal = inputFacts.recordedReversal(documentId);
        if (reversal != null) return new Result(documentId.toString(), document.getStatus(), "VOIDED", reversal.getId(),
                MaterialIssueSubmissionStatus.SUCCEEDED.getValue(), reversal.getOccurredAt(), reversal.getRecordedAt());
        if (recovering || !StockDocumentStatus.POSTED.getValue().equals(document.getStatus())
                || input.getConfirmedAt() == null || !Boolean.TRUE.equals(input.getStockApplied()))
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        String operationId = UUID.randomUUID().toString();
        // Both contribution removal and inverse stock action are in this LOCAL transaction.
        input.setStockApplied(false);
        if (inputFacts.updateVoid(input) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        quantities.validateInputReduction(input.getExecutionId());
        inputFacts.voidConfirmed(documentId, operationId);
        reversal = inputFacts.recordedReversal(documentId);
        if (reversal == null) throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        return new Result(documentId.toString(), document.getStatus(), "VOIDED", reversal.getId(), MaterialIssueSubmissionStatus.SUCCEEDED.getValue(),
                reversal.getOccurredAt(), reversal.getRecordedAt());
    }

    private Result result(Long documentId, String status, Object ignored) {
        return new Result(documentId.toString(), status, StockDocumentStatus.CANCELLED.getValue(), null, null, null, null);
    }

    public record Result(String documentId, String documentStatus, String productionInputStatus,
                         String voidOperationId, String voidResultStatus,
                         LocalDateTime voidRequestedAt, LocalDateTime voidFinishedAt) {
    }
}
