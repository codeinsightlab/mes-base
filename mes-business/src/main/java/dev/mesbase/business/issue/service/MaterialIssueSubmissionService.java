package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueSubmissionMapper;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueResource;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionEntity;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** LOCAL input confirmation participates in the document owner's transaction. */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueSubmissionService {
    private static final String LOCAL_SOURCE = Mode.LOCAL_ONLY.getValue();
    private final BusinessSourceRules sources;
    private final MesMaterialIssueMapper issues;
    private final MesMaterialIssueItemMapper items;
    private final MesMaterialIssueSubmissionMapper attempts;
    private final MaterialIssueRules rules;
    private final ProductionChainLocks locks;
    private final ProductionExecutionRules executions;
    private final ExecutionPathService paths;
    private final MaterialIssueInputFacts inputFacts;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueSubmissionService(MesMaterialIssueMapper issues, MesMaterialIssueItemMapper items,
                                          MesMaterialIssueSubmissionMapper attempts,
                                          MaterialIssueRules rules, ProductionChainLocks locks,
                                          ProductionExecutionRules executions, ExecutionPathService paths,
                                          MaterialIssueInputFacts inputFacts, BusinessSourceRules sources) {
        this.sources = sources;
        this.issues = issues;
        this.items = items;
        this.attempts = attempts;
        this.rules = rules;
        this.locks = locks;
        this.executions = executions;
        this.paths = paths;
        this.inputFacts = inputFacts;
    }

    public record Prepared(MesMaterialIssueEntity row, Long submissionId, Integer pathRevision,
                           boolean confirmationRequired) {
    }

    private record LocalLine(Long itemId, Long materialId, String materialCode, String batchNo,
                             BigDecimal quantity, Long unitId, String unitCode, Long warehouseId,
                             String warehouseCode) {
    }

    private record LocalInput(String requestId, String inputKind, BigDecimal outputQty, String calculationJson) {
    }

    private record LocalConfirmation(Long issueId, String operationKey, String issueNo, Long workorderId,
                                     Long executionId, Long productId, String productCode, Long routingId,
                                     Integer pathRevision, List<Long> routingNodeIds, LocalDateTime issueTime,
                                     List<LocalLine> lines, LocalInput input) {
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity submit(Long id) {
        return executePrepared(prepare(id, FactoryContext.required()));
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public Prepared prepare(Long id, FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        sources.localCreation();
        rules.lockIssues(List.of(id));
        var row = issues.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        rules.requireLocalFacts(row);
        if (row.getSubmissionId() != null)
            return new Prepared(row, row.getSubmissionId(), null, false);
        if (row.getVoidOperationId() != null || !Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue())
                .contains(row.getStatus()) || row.getExecutionId() == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var execution = locks.execution(row.getExecutionId());
        rules.validateExecutionSource(execution);
        if (!Set.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(execution.getStatus()) || !Objects.equals(row.getWorkorderId(), execution.getWorkorderId())
                || !Objects.equals(row.getProductId(), execution.getProductId()) || !Objects.equals(row.getRoutingId(), execution.getRoutingId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var input = inputFacts.find(id);
        if (input == null || !LOCAL_SOURCE.equals(input.getSourceMode()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        if (ProductionExecutionStatus.READY.getValue().equals(execution.getStatus())) executions.checkReadyReferences(execution);
        var path = paths.requireSelected(execution);
        if (ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())
                && !ExecutionPathStatus.LOCKED.getValue().equals(path.getPathStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var filter = new MesMaterialIssueItemExample();
        filter.createCriteria().andIssueIdEqualTo(id);
        filter.setOrderByClause("id");
        long count = items.countByExample(filter);
        if (count < 1 || count > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var detail = items.selectByExample(filter);
        detail.forEach(rules::validate);
        var calculation = json.readValue(input.getCalculationJson(), Calculation.class);
        if (!LOCAL_SOURCE.equals(calculation.sourceMode())) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var attempt = new MesMaterialIssueSubmissionEntity();
        attempt.setIssueId(id);
        attempt.setPathRevision(path.getRevision());
        attempt.setCommandJson("{}");
        attempt.setStatus(MaterialIssueSubmissionStatus.SUBMITTING.getValue());
        attempt.setCreatedAt(LocalDateTime.now());
        changed(attempts.insert(attempt));
        var command = new LocalConfirmation(id, "ISSUE-SUBMIT-" + attempt.getId(), row.getIssueNo(),
                row.getWorkorderId(), execution.getId(), row.getProductId(), row.getProductCode(), execution.getRoutingId(),
                path.getRevision(), paths.view(execution.getId()).selectedNodeIds(), row.getIssueTime(),
                detail.stream().map(MaterialIssueSubmissionService::line).toList(),
                new LocalInput(input.getId(), input.getInputKind(), input.getOutputQty(), input.getCalculationJson()));
        attempt.setCommandJson(json.writeValueAsString(command));
        changed(attempts.updateByPrimaryKeyWithBLOBs(attempt));
        row.setSubmissionId(attempt.getId());
        row.setStatus(MaterialIssueStatus.SUBMITTING.getValue());
        row.setUpdateTime(LocalDateTime.now());
        changed(issues.updateByPrimaryKey(row));
        return new Prepared(row, attempt.getId(), path.getRevision(), true);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity executePrepared(Prepared prepared) {
        Long id = prepared.row().getId();
        rules.lockIssues(List.of(id));
        var row = issues.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        rules.requireLocalFacts(row);
        if (!prepared.confirmationRequired()) return row;
        var attempt = attempts.selectByPrimaryKey(prepared.submissionId());
        if (attempt == null || !Objects.equals(attempt.getIssueId(), id)
                || !Objects.equals(row.getSubmissionId(), prepared.submissionId())
                || !Objects.equals(attempt.getPathRevision(), prepared.pathRevision())
                || !MaterialIssueSubmissionStatus.SUBMITTING.getValue().equals(attempt.getStatus())
                || !MaterialIssueStatus.SUBMITTING.getValue().equals(row.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var execution = locks.execution(row.getExecutionId());
        inputFacts.confirm(row, LOCAL_SOURCE);
        if (ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(execution.getStatus()))
            executions.resumeAfterInput(execution);
        paths.confirm(execution, attempt.getPathRevision());
        row.setStatus(MaterialIssueStatus.CONFIRMED.getValue());
        row.setUpdateTime(LocalDateTime.now());
        attempt.setStatus(MaterialIssueSubmissionStatus.SUCCEEDED.getValue());
        attempt.setFinishedAt(LocalDateTime.now());
        changed(attempts.updateByPrimaryKeyWithBLOBs(attempt));
        changed(issues.updateByPrimaryKey(row));
        return row;
    }

    private static LocalLine line(MesMaterialIssueItemEntity row) {
        return new LocalLine(row.getId(), row.getMaterialId(), row.getMaterialCode(), row.getBatchNo(), row.getQuantity(),
                row.getUnitId(), row.getUnitCode(), row.getWarehouseId(), row.getWarehouseCode());
    }

    private static void changed(int count) {
        if (count != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }
}
