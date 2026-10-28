package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
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

/** LOCAL production-input reversal, stored on the production-input fact and unified stock document. */
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
            if (recovering) throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
            if (!StockDocumentStatus.DRAFT.getValue().equals(document.getStatus()))
                throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
            documents.cancel(documentId, new StockDocumentModels.Version(document.getRevision()));
            return result(documentId, StockDocumentStatus.CANCELLED.getValue(), null);
        }
        if (!"LOCAL_ONLY".equals(input.getProductionSourceMode()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        Long initialExecutionId = input.getSourceExecutionId();
        executions.lock(initialExecutionId);
        // All production commands lock WorkOrder -> Execution -> StockDocument. Re-read both
        // mutable facts only after the shared execution lock, so a waiter observes the winner.
        document = documents.lockMaterialIssueForVoid(documentId);
        input = inputFacts.find(documentId);
        if (input == null || !Objects.equals(initialExecutionId, input.getSourceExecutionId())
                || !Objects.equals(document.getSourceExecutionId(), input.getSourceExecutionId()))
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        if (!"LOCAL_ONLY".equals(input.getProductionSourceMode()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (input.getProductionVoidOperationId() != null
                && MaterialIssueSubmissionStatus.SUCCEEDED.getValue().equals(input.getProductionVoidResultStatus()))
            return result(documentId, document.getStatus(), input);
        if (recovering && input.getProductionVoidOperationId() == null)
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        if (!recovering && input.getProductionVoidOperationId() != null)
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        if (!StockDocumentStatus.POSTED.getValue().equals(document.getStatus())
                || input.getProductionConfirmedAt() == null || !Boolean.TRUE.equals(input.getProductionStockApplied()))
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        if (!recovering) {
            input.setProductionVoidOperationId(UUID.randomUUID().toString());
            input.setProductionVoidFromStatus(document.getStatus());
            input.setProductionVoidRequestedAt(LocalDateTime.now());
            input.setProductionVoidResultStatus(MaterialIssueSubmissionStatus.SUBMITTING.getValue());
            save(input);
        }
        // Pending reversal output is excluded from confirmed production-input quantity.
        // Validate only after marking this input pending, in the same transaction.
        quantities.validateInputReduction(input.getSourceExecutionId());
        inputFacts.voidConfirmed(documentId, input.getProductionVoidOperationId());
        input.setProductionStockApplied(false);
        input.setProductionVoidResultStatus(MaterialIssueSubmissionStatus.SUCCEEDED.getValue());
        input.setProductionVoidFinishedAt(LocalDateTime.now());
        save(input);
        return result(documentId, documents.detail(documentId).document().getStatus(), input);
    }

    private void save(WmStockDocumentEntity input) {
        if (inputFacts.updateVoid(input) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private Result result(Long documentId, String documentStatus, WmStockDocumentEntity input) {
        if (input == null) return new Result(documentId.toString(), documentStatus, "CANCELLED", null, null, null, null);
        String productionStatus = switch (input.getProductionVoidResultStatus() == null ? "" : input.getProductionVoidResultStatus()) {
            case "SUCCEEDED" -> "VOIDED";
            case "SUBMITTING" -> "VOID_PENDING";
            case "UNKNOWN" -> "VOID_UNKNOWN";
            default -> "CONFIRMED";
        };
        return new Result(documentId.toString(), documentStatus, productionStatus, input.getProductionVoidOperationId(),
                input.getProductionVoidResultStatus(), input.getProductionVoidRequestedAt(), input.getProductionVoidFinishedAt());
    }

    public record Result(String documentId, String documentStatus, String productionInputStatus,
                         String voidOperationId, String voidResultStatus,
                         LocalDateTime voidRequestedAt, LocalDateTime voidFinishedAt) { }
}
