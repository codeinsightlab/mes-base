package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
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
import java.util.Objects;
import java.util.Set;
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

    public record Prepared(Long documentId, Integer pathRevision, boolean confirmationRequired, String postRequestId) { }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public Prepared prepare(Long documentId, FactoryScope scope) {
        var input = facts.find(documentId);
        if (input == null) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        return prepare(documentId, scope, input.getId());
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public Prepared prepare(Long documentId, FactoryScope scope, String postRequestId) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        sources.localCreation();
        var row = documents.getObject().detail(documentId).document();
        var input = facts.find(documentId);
        if (input == null || !StockDocumentType.MATERIAL_ISSUE.getValue().equals(row.getDocumentType())
                || !Mode.LOCAL_ONLY.getValue().equals(input.getSourceMode())
                || !Objects.equals(row.getSourceExecutionId(), input.getExecutionId()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        var execution = locks.execution(input.getExecutionId());
        var path = paths.requireSelected(execution);
        if (input.getConfirmedAt() != null) {
            if (!StockDocumentStatus.POSTED.getValue().equals(row.getStatus()) || !Boolean.TRUE.equals(input.getStockApplied()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return new Prepared(documentId, path.getRevision(), false, postRequestId);
        }
        if (!StockDocumentStatus.DRAFT.getValue().equals(row.getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        sources.execution(execution, null);
        if (!Set.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue(),
                ProductionExecutionStatus.FULLY_RECEIVED.getValue()).contains(execution.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (ProductionExecutionStatus.READY.getValue().equals(execution.getStatus())) executions.checkReadyReferences(execution);
        if (ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())
                && !ExecutionPathStatus.LOCKED.getValue().equals(path.getPathStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return new Prepared(documentId, path.getRevision(), true, postRequestId);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public WmStockDocumentEntity executePrepared(Prepared prepared) {
        var input = facts.find(prepared.documentId());
        if (input == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var execution = locks.execution(input.getExecutionId());
        if (prepared.confirmationRequired()) {
            facts.confirm(prepared.documentId(), execution.getId(), Mode.LOCAL_ONLY.getValue(), prepared.postRequestId());
            if (ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(execution.getStatus())) executions.resumeAfterInput(execution);
            paths.confirm(execution, prepared.pathRevision());
        }
        return documents.getObject().detail(prepared.documentId()).document();
    }
}
