package dev.mesbase.business.issue.service;

import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.integration.warehouse.ExternalEffect;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.business.issue.domain.MesMaterialIssueResource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueVoidService {
    private static final Logger LOG = LoggerFactory.getLogger(MaterialIssueVoidService.class);
    private final MesMaterialIssueMapper issues;
    private final WorkorderMapper orders;
    private final MaterialIssueRules rules;
    private final ProductionExecutionRules executions;
    private final WarehouseIntegration warehouse;
    private final MaterialIssueInputFacts inputFacts;
    private final MaterialIssueSubmissionService submissions;
    private final ProductionQuantityService quantities;
    private final TransactionTemplate transaction;

    public MaterialIssueVoidService(MesMaterialIssueMapper issues, WorkorderMapper orders, MaterialIssueRules rules,
                                    ProductionExecutionRules executions, WarehouseIntegration warehouse,
                                    MaterialIssueInputFacts inputFacts, ProductionQuantityService quantities,
                                    MaterialIssueSubmissionService submissions,
                                    @Qualifier("businessTransactionManager") PlatformTransactionManager manager) {
        this.issues = issues;
        this.orders = orders;
        this.rules = rules;
        this.executions = executions;
        this.warehouse = warehouse;
        this.inputFacts = inputFacts;
        this.submissions = submissions;
        this.quantities = quantities;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity cancel(Long id) {
        return perform(id, false);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity reconcile(Long id) {
        return perform(id, true);
    }

    private record Prepared(MesMaterialIssueEntity row, boolean invoke, Long version) {
    }

    private MesMaterialIssueEntity perform(Long id, boolean query) {
        FactoryScope scope = FactoryContext.required();
        var prepared = transaction.execute(s -> {
            var row = lock(id);
            row.setSourceContext(submissions.source(row));
            if (query) {
                if (!Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                        .contains(row.getStatus()))
                    return new Prepared(row, false, rules.extraVersion(id));
            } else if (row.getVoidOperationId() != null || MaterialIssueStatus.VOIDED.getValue()
                    .equals(row.getStatus()))
                return new Prepared(row, false, rules.extraVersion(id));
            if (!query) {
                if (!Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue(), MaterialIssueStatus.CONFIRMED.getValue())
                        .contains(row.getStatus()))
                    throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
                if (row.getExecutionId() != null) {
                    var e = executions.lock(row.getExecutionId());
                    if (inputFacts.find(id) == null && (e.getStartTime() != null || Set.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.COMPLETED.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                            .contains(e.getStatus())) || Set.of(ProductionExecutionStatus.COMPLETED.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue(), ProductionExecutionStatus.CANCELLED.getValue())
                            .contains(e.getStatus()))
                        throw new FoundationException(ErrorCode.EXECUTION_REFERENCED);
                }
                Mode mode = warehouse.cancellationMode(scope, row);
                row.setVoidOperationId(UUID.randomUUID().toString());
                row.setVoidFromStatus(row.getStatus());
                row.setVoidExecutionMode(mode.getValue());
                row.setVoidRequestedAt(LocalDateTime.now());
                row.setStatus(MaterialIssueStatus.VOID_PENDING.getValue());
                save(row);
                if (row.getExecutionId() != null && inputFacts.find(id) != null)
                    quantities.validateInputReduction(row.getExecutionId());
            }
            return new Prepared(row, true, rules.extraVersion(id));
        });
        var claim = prepared;
        if (!claim.invoke()) return claim.row();
        var record = claim.row();
        if (!Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                .contains(record.getStatus())) return record;
        Mode recordedMode = Mode.from(record.getVoidExecutionMode());
        if (recordedMode == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        // A repeated cancellation never repeats an external write; reconciliation only queries the same operation.
        ErpActionResult result;
        try {
            result = warehouse.execute(scope, record, query);
        } catch (Exception failure) {
            LOG.warn("Warehouse cancellation outcome unknown; operation {}", record.getVoidOperationId(), failure);
            result = new ErpActionResult(Status.UNKNOWN, recordedMode, record.getExternalReference(), "外部调用结果待核实");
        }
        if (result == null || result.status() == null || result.executionMode() == null || !result.executionMode()
                .getValue()
                .equals(record.getVoidExecutionMode()) || result.executionMode() == Mode.CONNECTED && !Objects.equals(result.externalReference(), record.getExternalReference()))
            result = new ErpActionResult(Status.UNKNOWN, recordedMode, record.getExternalReference(), "外部结果契约不完整或来源不符");
        ErpActionResult confirmed = result;
        return transaction.execute(s -> {
            var row = lock(id);
            if (!claim.version().equals(rules.extraVersion(id)) || !record.getVoidOperationId()
                    .equals(row.getVoidOperationId()) || !Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                    .contains(row.getStatus())) return row;
            row.setVoidResultStatus(confirmed.status().getValue());
            row.setVoidMessage(confirmed.status() == Status.SUCCEEDED ? "作废结果已确认" : confirmed.status() == Status.REJECTED ? "ERP明确拒绝作废，单据仍有效" : "外部结果待核实");
            if (confirmed.status() == Status.SUCCEEDED) {
                inputFacts.voidConfirmed(row);
                row.setStatus(MaterialIssueStatus.VOIDED.getValue());
                row.setExternalEffect(confirmed.executionMode() == Mode.LOCAL_ONLY ? ExternalEffect.NONE.getValue() : ExternalEffect.REVERSED.getValue());
                row.setVoidFinishedAt(LocalDateTime.now());
            } else if (confirmed.status() == Status.REJECTED) {
                row.setStatus(row.getVoidFromStatus());
                row.setVoidFinishedAt(LocalDateTime.now());
            } else row.setStatus(MaterialIssueStatus.VOID_UNKNOWN.getValue());
            save(row);
            return row;
        });
    }

    private MesMaterialIssueEntity lock(Long id) {
        rules.lockIssues(List.of(id));
        var row = rules.hydrate(issues.selectByPrimaryKey(id));
        if (row.getWorkorderId() != null) {
            if (orders.selectByPrimaryKeyForUpdate(row.getWorkorderId()) == null)
                throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    private void save(MesMaterialIssueEntity row) {
        rules.audit(row, false);
        if (issues.updateByPrimaryKey(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
