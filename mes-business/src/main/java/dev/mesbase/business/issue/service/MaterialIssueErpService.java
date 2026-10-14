package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueResource;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.QueryMaterialIssueCommand;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueErpService {
    private static final Logger LOG = LoggerFactory.getLogger(MaterialIssueErpService.class);
    private final MaterialIssueSubmissionService submissions;
    private final MesMaterialIssueMapper issues;
    private final MaterialIssueErpFacts facts;
    private final MaterialIssueRules rules;
    private final WarehouseIntegration warehouse;
    private final TransactionTemplate read, write;

    public MaterialIssueErpService(MesMaterialIssueMapper issues, MaterialIssueErpFacts facts, MaterialIssueRules rules,
                                   WarehouseIntegration warehouse, MaterialIssueSubmissionService submissions,
                                   @Qualifier("businessTransactionManager") PlatformTransactionManager manager) {
        this.submissions = submissions;
        this.issues = issues;
        this.facts = facts;
        this.rules = rules;
        this.warehouse = warehouse;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public record QueryView(ErpIssueQueryResult result, String mesStatus, String provider, String lastOutcome,
                            String lastMessage, LocalDateTime checkedAt) {
    }

    private record Snapshot(MesMaterialIssueEntity issue, MesMaterialIssueErpExtraEntity extra) {
    }

    private Snapshot snapshot(Long id) {
        return read.execute(s -> {
            var row = issues.selectByPrimaryKey(id);
            if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
            return new Snapshot(row, facts.required(id));
        });
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public QueryView queryErpStatus(Long id) {
        FactoryScope scope = FactoryContext.required();
        var snapshot = snapshot(id);
        var result = query(scope, snapshot);
        var e = snapshot.extra();
        return new QueryView(result, snapshot.issue()
                .getStatus(), e.getProvider(), e.getLastOutcome(), e.getLastMessage(), e.getCheckedAt());
    }

    private ErpIssueQueryResult query(FactoryScope scope, Snapshot snapshot) {
        var e = snapshot.extra();
        var row = snapshot.issue();
        var command = new QueryMaterialIssueCommand(row.getId(), row.getVoidOperationId() != null ? row.getVoidOperationId() : submissions.operationKey(row), e.getExternalBindingKey(), e.getExternalReference(), e.getExternalCode());
        ErpIssueQueryResult result;
        try {
            result = warehouse.query(scope, e.getSourceExecutionMode(), e.getProvider(), command);
        } catch (FoundationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            LOG.warn("ERP query uncertain for issue {} operation {}", row.getId(), row.getVoidOperationId(), failure);
            return ErpIssueQueryResult.unknown(Mode.CONNECTED);
        }
        if (result == null || result.outcome() == null || result.documentStatus() == null || result.executionMode() == null
                || !result.executionMode().name().equals(e.getSourceExecutionMode()))
            return ErpIssueQueryResult.unknown(Mode.valueOf(e.getSourceExecutionMode()));
        if (result.executionMode() == Mode.LOCAL_ONLY) return ErpIssueQueryResult.local();
        if (result.outcome() == Outcome.FOUND && (result.externalId() == null && result.externalCode() == null
                || e.getExternalReference() != null && !Objects.equals(e.getExternalReference(), result.externalId())
                || e.getExternalCode() != null && !Objects.equals(e.getExternalCode(), result.externalCode())))
            return ErpIssueQueryResult.unknown(Mode.CONNECTED);
        // Vendor diagnostics never pass through to HTTP or persistence; the coordinator emits a safe standard summary.
        return new ErpIssueQueryResult(result.outcome(), result.outcome() == Outcome.FOUND ? result.documentStatus() : DocumentStatus.UNKNOWN,
                Mode.CONNECTED, result.outcome() == Outcome.FOUND ? result.externalId() : e.getExternalReference(), result.outcome() == Outcome.FOUND ? result.externalCode() : e.getExternalCode(),
                result.outcome() == Outcome.FOUND ? "已查询ERP业务状态" : result.outcome() == Outcome.NOT_FOUND ? "本次未找到ERP单据，不能据此重建或标记创建失败" : "ERP结果不确定，请人工核实");
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public QueryView reconcileErpStatus(Long id) {
        FactoryScope scope = FactoryContext.required();
        var snapshot = snapshot(id);
        var result = query(scope, snapshot);
        return write.execute(s -> {
            rules.lockIssues(List.of(id));
            var row = rules.hydrate(issues.selectByPrimaryKey(id));
            var extra = facts.required(id);
            if (!Objects.equals(extra.getOperationVersion(), snapshot.extra().getOperationVersion())
                    || !Objects.equals(row.getVoidOperationId(), snapshot.issue().getVoidOperationId())
                    || !Objects.equals(row.getSubmissionId(), snapshot.issue().getSubmissionId())
                    || !Objects.equals(row.getStatus(), snapshot.issue().getStatus()))
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            String message = submissions.reconcileSubmission(row, extra, result);
            if (message == null) message = MaterialIssueErpRules.reconcile(row, extra, result);
            extra.setLastOperation("RECONCILE");
            extra.setLastOutcome(result.outcome().name());
            extra.setLastMessage(message);
            extra.setCheckedAt(LocalDateTime.now());
            if (issues.updateByPrimaryKey(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            facts.save(extra);
            return new QueryView(result, row.getStatus(), extra.getProvider(), extra.getLastOutcome(), extra.getLastMessage(), extra.getCheckedAt());
        });
    }
}
