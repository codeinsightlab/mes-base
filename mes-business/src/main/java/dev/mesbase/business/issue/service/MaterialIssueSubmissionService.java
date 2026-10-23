package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult;
import dev.mesbase.business.integration.warehouse.ExternalEffect;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand.Line;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand.Source;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration.Selection;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueSubmissionMapper;
import dev.mesbase.business.issue.domain.MaterialIssueOperation;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueResource;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionEntity;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
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
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueSubmissionService {
    private final BusinessSourceRules sources;
    private static final Logger LOG = LoggerFactory.getLogger(MaterialIssueSubmissionService.class);
    private final MesMaterialIssueMapper issues;
    private final MesMaterialIssueItemMapper items;
    private final MesMaterialIssueSubmissionMapper attempts;
    private final WorkorderErpExtraMapper orderFacts;
    private final MaterialIssueErpFacts facts;
    private final MaterialIssueRules rules;
    private final ProductionChainLocks locks;
    private final ProductionExecutionRules executions;
    private final ExecutionPathService paths;
    private final WarehouseIntegration warehouse;
    private final MaterialIssueInputFacts inputFacts;
    private final TransactionTemplate transaction;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueSubmissionService(MesMaterialIssueMapper issues, MesMaterialIssueItemMapper items,
                                          MesMaterialIssueSubmissionMapper attempts,
                                          WorkorderErpExtraMapper orderFacts, MaterialIssueErpFacts facts,
                                          MaterialIssueRules rules, ProductionChainLocks locks,
                                          ProductionExecutionRules executions, ExecutionPathService paths,
                                          WarehouseIntegration warehouse, MaterialIssueInputFacts inputFacts,
                                          @Qualifier("businessTransactionManager") PlatformTransactionManager manager,
                                          BusinessSourceRules sources) {
        this.sources = sources;
        this.issues = issues;
        this.items = items;
        this.attempts = attempts;
        this.orderFacts = orderFacts;
        this.facts = facts;
        this.rules = rules;
        this.locks = locks;
        this.executions = executions;
        this.paths = paths;
        this.warehouse = warehouse;
        this.inputFacts = inputFacts;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public record Prepared(MesMaterialIssueEntity row, MaterialIssueSubmitCommand command, Selection selection,
                           Long version) {
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity submit(Long id) {
        FactoryScope scope = FactoryContext.required();
        Prepared prepared = transaction.execute(s -> prepare(id, scope));
        return executePrepared(scope, prepared);
    }

    public MesMaterialIssueEntity executePrepared(FactoryScope scope, Prepared prepared) {
        Long id = prepared.row().getId();
        if (prepared.command() == null) return prepared.row();
        ErpActionResult result;
        try {
            result = warehouse.createMaterialIssue(scope, prepared.selection(), prepared.command());
        } catch (RuntimeException failure) {
            LOG.warn("Material issue submission outcome unknown; operation {}", prepared.command()
                    .operationKey(), failure);
            result = new ErpActionResult(Status.UNKNOWN, prepared.selection().mode(), null, "领料结果待核实");
        }
        ErpActionResult confirmed = safe(result, prepared.selection().mode());
        return transaction.execute(s -> {
            rules.lockIssues(List.of(id));
            var row = rules.hydrate(issues.selectByPrimaryKey(id));
            var e = facts.required(id);
            if (!Objects.equals(row.getSubmissionId(), prepared.row()
                    .getSubmissionId()) || !Objects.equals(e.getOperationVersion(), prepared.version()) || !MaterialIssueStatus.SUBMITTING.getValue()
                    .equals(row.getStatus()))
                return row;
            apply(row, e, confirmed);
            changed(issues.updateByPrimaryKey(row));
            facts.save(e);
            return rules.hydrate(row);
        });
    }

    public Prepared prepare(Long id, FactoryScope scope) {
        rules.lockIssues(List.of(id));
        var row = rules.hydrate(issues.selectByPrimaryKey(id));
        if (row.getSubmissionId() != null) return new Prepared(row, null, null, null);
        if (row.getVoidOperationId() != null || !Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue())
                .contains(row.getStatus()) || row.getExecutionId() == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var e = locks.execution(row.getExecutionId());
        rules.validateExecutionSource(e);
        if (!Set.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                .contains(e.getStatus()) || !Objects.equals(row.getWorkorderId(), e.getWorkorderId()) || !Objects.equals(row.getProductId(), e.getProductId()) || !Objects.equals(row.getRoutingId(), e.getRoutingId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(e.getStatus()) && inputFacts.find(id) == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (ProductionExecutionStatus.READY.getValue().equals(e.getStatus())) executions.checkReadyReferences(e);
        var path = paths.requireSelected(e);
        if (ProductionExecutionStatus.RUNNING.getValue().equals(e.getStatus()) && !ExecutionPathStatus.LOCKED.getValue()
                .equals(path.getPathStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var f = new MesMaterialIssueItemExample();
        f.createCriteria().andIssueIdEqualTo(id);
        f.setOrderByClause("id");
        if (items.countByExample(f) < 1 || items.countByExample(f) > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var lines = items.selectByExample(f);
        // At most 100 lines, existing reference/quantity validation; no inventory or mandatory batch invention.
        lines.forEach(rules::validate);
        var extra = facts.required(id);
        if (extra.getSourceExecutionMode() == null || extra.getExternalReference() != null || extra.getExternalCode() != null || ExternalEffect.UNVERIFIED.getValue()
                .equals(extra.getExternalEffect()) && !Set.of(MaterialIssueOperation.LOCAL_RECORD.getValue(), MaterialIssueOperation.REOPEN_REJECTED.getValue())
                .contains(extra.getLastOperation())) throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        var selection = warehouse.submissionSelection(scope);
        if (!selection.mode().getValue().equals(extra.getSourceExecutionMode()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var attempt = new MesMaterialIssueSubmissionEntity();
        attempt.setIssueId(id);
        attempt.setPathRevision(path.getRevision());
        attempt.setCommandJson("{}");
        attempt.setStatus(MaterialIssueSubmissionStatus.SUBMITTING.getValue());
        attempt.setCreatedAt(LocalDateTime.now());
        changed(attempts.insert(attempt));
        var external = orderFacts.selectByPrimaryKey(e.getWorkorderId());
        var integrationSnapshot = FactoryIntegrationContext.required();
        var externalWarehouses = selection.mode() == Mode.CONNECTED ? sources.externalWarehouseIds(lines.stream()
                .map(line -> line.getWarehouseId()).toList()) : Map.<Long, String>of();
        var command = new MaterialIssueSubmitCommand(id, "ISSUE-SUBMIT-" + attempt.getId(), row.getIssueNo(), row.getWorkorderId(), row.getWorkorderCode(), Optional.ofNullable(external)
                .map(nullableValue -> nullableValue.getExternalId()).orElse(null), Optional.ofNullable(external)
                .map(nullableValue -> nullableValue.getExternalCode())
                .orElse(null), e.getId(), row.getProductId(), row.getProductCode(), e.getRoutingId(), path.getRevision(), paths.view(e.getId())
                .selectedNodeIds(), Objects.toString(row.getIssueTime(), null), lines.stream()
                .map(i -> new Line(i.getId(), i.getMaterialId(), i.getMaterialCode(), i.getBatchNo(), i.getQuantity(), i.getUnitId(), i.getUnitCode(), i.getWarehouseId(), i.getWarehouseCode(), externalWarehouses.get(i.getWarehouseId())))
                .toList(), inputFacts.snapshot(id), new Source(selection.provider(), selection.bindingKey(),
                integrationSnapshot.revision(),
                integrationSnapshot.configurationRevision()));
        attempt.setCommandJson(json.writeValueAsString(command));
        changed(attempts.updateByPrimaryKeyWithBLOBs(attempt));
        row.setSubmissionId(attempt.getId());
        row.setStatus(MaterialIssueStatus.SUBMITTING.getValue());
        row.setUpdateTime(LocalDateTime.now());
        changed(issues.updateByPrimaryKey(row));
        extra.setProvider(selection.provider());
        extra.setExternalBindingKey(selection.bindingKey());
        extra.setLastOperation(MaterialIssueOperation.SUBMIT.getValue());
        extra.setLastOutcome(Status.UNKNOWN.getValue());
        extra.setLastMessage("领料提交已准备，等待可信结果");
        facts.save(extra);
        return new Prepared(row, command, selection, extra.getOperationVersion());
    }

    private ErpActionResult safe(ErpActionResult result, Mode mode) {
        if (result == null || result.status() == null || result.executionMode() != mode)
            return new ErpActionResult(Status.UNKNOWN, mode, null, "领料结果契约不完整");
        if (mode == Mode.LOCAL_ONLY)
            return new ErpActionResult(result.status(), mode, null, null, "本地模式，领料已确认");
        if (result.status() == Status.SUCCEEDED && (result.externalReference() == null || result.externalReference()
                .isBlank()))
            return new ErpActionResult(Status.UNKNOWN, mode, result.externalReference(), result.externalCode(), "外部身份缺失，待核实");
        if (result.status() == Status.SUCCEEDED && !result.stockEffective())
            return new ErpActionResult(Status.UNKNOWN, mode, result.externalReference(), result.externalCode(), "ERP单据已创建，库存生效待核实");
        return new ErpActionResult(result.status(), mode, result.externalReference(), result.externalCode(), "领料结果已记录", result.stockEffective());
    }

    /**
     * Caller holds chain locks. MES confirmation, attempt and Extra commit atomically with path lock.
     */
    public void apply(MesMaterialIssueEntity row, MesMaterialIssueErpExtraEntity extra, ErpActionResult result) {
        if (result.executionMode() == Mode.CONNECTED && result.status() == Status.SUCCEEDED && !result.stockEffective())
            result = new ErpActionResult(Status.UNKNOWN, Mode.CONNECTED, result.externalReference(), result.externalCode(), "ERP库存生效待核实");
        var a = attempts.selectByPrimaryKey(row.getSubmissionId());
        if (a == null || !Objects.equals(a.getIssueId(), row.getId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (result.executionMode() == Mode.CONNECTED) {
            if (result.externalReference() != null) extra.setExternalReference(result.externalReference());
            if (result.externalCode() != null) extra.setExternalCode(result.externalCode());
        }
        extra.setLastOperation(MaterialIssueOperation.SUBMIT.getValue());
        extra.setLastOutcome(result.status().getValue());
        extra.setLastMessage(result.status() == Status.SUCCEEDED ? (result.executionMode() == Mode.LOCAL_ONLY ? "本地模式，领料已确认" : "ERP领料业务已确认") : result.status() == Status.REJECTED ? "领料业务明确拒绝，保留原提交记录" : "领料结果待核实，不得重复创建");
        if (result.status() == Status.SUCCEEDED) {
            var execution = locks.execution(row.getExecutionId());
            inputFacts.confirm(row, result.executionMode().getValue());
            if (inputFacts.find(row.getId()) != null && ProductionExecutionStatus.FULLY_RECEIVED.getValue()
                    .equals(execution.getStatus()))
                executions.resumeAfterInput(execution);
            paths.confirm(execution, a.getPathRevision());
            row.setStatus(MaterialIssueStatus.CONFIRMED.getValue());
            a.setStatus(MaterialIssueSubmissionStatus.SUCCEEDED.getValue());
            a.setFinishedAt(LocalDateTime.now());
            extra.setExternalEffect(result.executionMode() == Mode.LOCAL_ONLY ? ExternalEffect.NONE.getValue() : ExternalEffect.CONFIRMED.getValue());
            if (result.executionMode() == Mode.CONNECTED) extra.setDocumentStatus(DocumentStatus.EFFECTIVE.getValue());
        } else if (result.status() == Status.REJECTED) {
            row.setStatus(MaterialIssueStatus.SUBMIT_REJECTED.getValue());
            a.setStatus(MaterialIssueSubmissionStatus.REJECTED.getValue());
            a.setFinishedAt(LocalDateTime.now());
        } else {
            row.setStatus(MaterialIssueStatus.SUBMIT_UNKNOWN.getValue());
            a.setStatus(MaterialIssueSubmissionStatus.UNKNOWN.getValue());
            extra.setExternalEffect(result.executionMode() == Mode.LOCAL_ONLY ? ExternalEffect.NONE.getValue() : ExternalEffect.UNKNOWN.getValue());
        }
        changed(attempts.updateByPrimaryKeyWithBLOBs(a));
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity reopenRejected(Long id) {
        return transaction.execute(s -> {
            rules.lockIssues(List.of(id));
            var row = rules.hydrate(issues.selectByPrimaryKey(id));
            var extra = facts.required(id);
            if (inputFacts.find(id) != null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            if (!MaterialIssueStatus.SUBMIT_REJECTED.getValue()
                    .equals(row.getStatus()) || row.getVoidOperationId() != null || extra.getExternalReference() != null || extra.getExternalCode() != null || !Status.REJECTED.getValue()
                    .equals(extra.getLastOutcome()) || !ProductionExecutionStatus.READY.getValue()
                    .equals(locks.execution(row.getExecutionId())
                            .getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            row.setSubmissionId(null);
            row.setStatus(MaterialIssueStatus.DRAFT.getValue());
            changed(issues.updateByPrimaryKey(row));
            extra.setExternalEffect(Mode.LOCAL_ONLY.getValue()
                    .equals(extra.getSourceExecutionMode()) ? ExternalEffect.NONE.getValue() : ExternalEffect.UNVERIFIED.getValue());
            // CONNECTED rejected with no external identity is a proven no-document case; new draft may submit explicitly again.
            extra.setLastOperation(MaterialIssueOperation.REOPEN_REJECTED.getValue());
            facts.save(extra);
            return rules.hydrate(row);
        });
    }

    public String reconcileSubmission(MesMaterialIssueEntity row, MesMaterialIssueErpExtraEntity extra,
                                      ErpIssueQueryResult result) {
        if (row.getSubmissionId() == null || !Set.of(MaterialIssueStatus.SUBMITTING.getValue(), MaterialIssueStatus.SUBMIT_UNKNOWN.getValue())
                .contains(row.getStatus()))
            return null;
        if (result.executionMode() == Mode.LOCAL_ONLY) {
            // Deterministic local domain confirmation for the original persisted operation; no external document exists.
            apply(row, extra, ErpActionResult.localSuccess());
            return extra.getLastMessage();
        }
        if (result.outcome() == Outcome.FOUND) {
            extra.setExternalReference(result.externalId());
            extra.setExternalCode(result.externalCode());
            extra.setDocumentStatus(result.documentStatus().getValue());
            if (result.documentStatus() == DocumentStatus.EFFECTIVE && result.externalId() != null) {
                apply(row, extra, new ErpActionResult(Status.SUCCEEDED, Mode.CONNECTED, result.externalId(), result.externalCode(), "原领料操作已核实", true));
                return extra.getLastMessage();
            }
            if (result.documentStatus() == DocumentStatus.REJECTED) {
                apply(row, extra, new ErpActionResult(Status.REJECTED, Mode.CONNECTED, result.externalId(), result.externalCode(), "原领料操作明确拒绝"));
                return extra.getLastMessage();
            }
        }
        return "原领料提交仍待核实，不得重复创建";
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Source source(MesMaterialIssueEntity row) {
        if (row.getSubmissionId() == null) return null;
        var attempt = attempts.selectByPrimaryKey(row.getSubmissionId());
        if (attempt == null || !Objects.equals(row.getId(), attempt.getIssueId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var node = json.readTree(attempt.getCommandJson());
        var source = node.get("source");
        // Historical attempts may contain only {} or predate the frozen source contract.
        if (source == null || source.isNull()) return null;
        return json.treeToValue(source, Source.class);
    }

    public String operationKey(MesMaterialIssueEntity row) {
        if (row.getSubmissionId() == null) return null;
        return "ISSUE-SUBMIT-" + row.getSubmissionId();
    }

    private static void changed(int n) {
        if (n != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

}
