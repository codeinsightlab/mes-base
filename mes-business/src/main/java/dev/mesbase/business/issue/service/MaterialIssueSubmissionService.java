package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Production confirmation state is a typed extension of the unified stock document. */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueSubmissionService {
    private final MaterialIssueInputFacts facts;
    private final ProductionChainLocks locks;
    private final ProductionExecutionRules executions;
    private final ExecutionPathService paths;
    private final BusinessSourceRules sources;
    private final ObjectProvider<StockDocumentService> documents;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueSubmissionService(MaterialIssueInputFacts facts, ProductionChainLocks locks,
            ProductionExecutionRules executions, ExecutionPathService paths, BusinessSourceRules sources,
            ObjectProvider<StockDocumentService> documents) {
        this.facts = facts;
        this.locks = locks;
        this.executions = executions;
        this.paths = paths;
        this.sources = sources;
        this.documents = documents;
    }

    public record Prepared(WmStockDocumentEntity row, Integer pathRevision, boolean confirmationRequired) { }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public Prepared prepare(Long documentId, FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        sources.localCreation();
        var detail = documents.getObject().detail(documentId);
        var row = detail.document();
        if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(row.getDocumentType())
                || facts.find(documentId) == null
                || !Mode.LOCAL_ONLY.getValue().equals(row.getProductionSourceMode())
                || !Objects.equals(row.getSourceExecutionId(), facts.find(documentId).getSourceExecutionId()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        var execution = locks.execution(row.getSourceExecutionId());
        if (row.getProductionVoidOperationId() != null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (row.getProductionConfirmationOperationId() != null) {
            if (MaterialIssueSubmissionStatus.SUCCEEDED.getValue().equals(row.getProductionConfirmationStatus())
                    && row.getProductionConfirmedAt() != null && Boolean.TRUE.equals(row.getProductionStockApplied()))
                return new Prepared(row, row.getProductionPathRevision(), false);
            // A persisted in-flight/unknown command is never reconstructed or submitted again.
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        if (!StockDocumentStatus.DRAFT.getValue().equals(row.getStatus()) || detail.lines().isEmpty())
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        sources.execution(execution, null);
        if (!Set.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue(),
                ProductionExecutionStatus.FULLY_RECEIVED.getValue()).contains(execution.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (ProductionExecutionStatus.READY.getValue().equals(execution.getStatus())) executions.checkReadyReferences(execution);
        var path = paths.requireSelected(execution);
        if (ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())
                && !ExecutionPathStatus.LOCKED.getValue().equals(path.getPathStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);

        row.setProductionPathRevision(path.getRevision());
        row.setProductionConfirmationOperationId("ISSUE-SUBMIT-" + UUID.randomUUID());
        row.setProductionConfirmationStatus(MaterialIssueSubmissionStatus.SUBMITTING.getValue());
        row.setProductionSubmissionCreatedAt(LocalDateTime.now());
        row.setProductionConfirmedPathNodeIds(json.writeValueAsString(paths.view(execution.getId()).selectedNodeIds()));
        if (facts.updateVoid(row) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return new Prepared(row, path.getRevision(), true);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public WmStockDocumentEntity executePrepared(Prepared prepared) {
        Long documentId = prepared.row().getId();
        var row = facts.find(documentId);
        if (row == null || !Objects.equals(row.getProductionPathRevision(), prepared.pathRevision()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var execution = locks.execution(row.getSourceExecutionId());
        if (!prepared.confirmationRequired()) return documents.getObject().detail(documentId).document();
        if (!MaterialIssueSubmissionStatus.SUBMITTING.getValue().equals(row.getProductionConfirmationStatus())
                || row.getProductionConfirmationOperationId() == null
                || row.getProductionSubmissionCreatedAt() == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        facts.confirm(documentId, execution.getId(), Mode.LOCAL_ONLY.getValue());
        if (ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(execution.getStatus())) executions.resumeAfterInput(execution);
        paths.confirm(execution, prepared.pathRevision());
        row = facts.find(documentId);
        row.setProductionConfirmationStatus(MaterialIssueSubmissionStatus.SUCCEEDED.getValue());
        row.setProductionSubmissionFinishedAt(LocalDateTime.now());
        if (facts.updateVoid(row) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return documents.getObject().detail(documentId).document();
    }
}
