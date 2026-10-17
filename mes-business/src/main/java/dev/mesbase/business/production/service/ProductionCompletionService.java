package dev.mesbase.business.production.service;

import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.shared.enums.BusinessSource;

import dev.mesbase.business.production.dao.MesExecutionCompletionMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.wm.dao.MesWarehouseAcceptanceMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import static dev.mesbase.business.production.domain.ProductionClosureRules.blocked;
import static dev.mesbase.business.production.domain.ProductionClosureRules.conflict;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionCompletionService {
    private final BusinessSourceRules sources;

    public record Command(String requestId, String expectedBasisHash, String reason) {
    }

    public record Summary(MesProductionExecutionEntity execution, QualityReleaseService.State release,
                          BigDecimal received, BigDecimal accepted, BigDecimal remainingHandover,
                          BigDecimal pendingAcceptance,
                          BigDecimal outputExcess, BigDecimal releaseExcess, MesExecutionCompletionEntity completion,
                          boolean canComplete, String blockedReason) {
    }

    private final MesExecutionCompletionMapper completions;
    private final MesProductionExecutionMapper executions;
    private final MesProductionReceiptMapper receipts;
    private final MesWarehouseAcceptanceMapper acceptances;
    private final MesMaterialIssueMapper issues;
    private final ProductionChainLocks locks;
    private final QualityReleaseService releases;
    private final ProductionExecutionRules rules;
    private final WarehouseIntegration integration;

    public ProductionCompletionService(MesExecutionCompletionMapper completions,
                                       MesProductionExecutionMapper executions,
                                       MesProductionReceiptMapper receipts, MesWarehouseAcceptanceMapper acceptances,
                                       MesMaterialIssueMapper issues,
                                       ProductionChainLocks locks, QualityReleaseService releases,
                                       ProductionExecutionRules rules, WarehouseIntegration integration,
                                       BusinessSourceRules sources) {
        this.sources = sources;
        this.completions = completions;
        this.executions = executions;
        this.receipts = receipts;
        this.acceptances = acceptances;
        this.issues = issues;
        this.locks = locks;
        this.releases = releases;
        this.rules = rules;
        this.integration = integration;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Summary summary(Long id) {
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) ProductionClosureRules.bad();
        var q = releases.state(id);
        var r = receipts.netQuantity(id);
        var a = acceptances.netExecution(id);
        var f = new MesExecutionCompletionExample();
        f.createCriteria().andExecutionIdEqualTo(id);
        MesExecutionCompletionEntity completed;
        try (var page = PageHelper.offsetPage(0, 2, false)) {
            var rows = completions.selectByExample(f);
            if (rows.size() > 1) conflict();
            completed = rows.isEmpty() ? null : rows.getFirst();
        }
        var unresolved = new MesMaterialIssueExample();
        unresolved.createCriteria().andExecutionIdEqualTo(id)
                .andStatusNotIn(List.of(MaterialIssueStatus.CONFIRMED.getValue(), MaterialIssueStatus.VOIDED.getValue(), MaterialIssueStatus.SUBMIT_REJECTED.getValue()));
        String reason = !ProductionExecutionStatus.RUNNING.getValue()
                .equals(execution.getStatus()) ? "执行必须处于RUNNING状态" : !q.safe() ? q.blockedReason() : !q.basisValid() ? "缺少当前生产质量依据下的明确放行" : issues.countByExample(unresolved) > 0 ? "相关领料仍有未决状态，请核实原请求" : r.compareTo(q.effective()) != 0 ? "末道有效产出尚未全部合法交接；未交接数量 " + q.effective()
                .subtract(r)
                .toPlainString() : r.compareTo(q.released()) > 0 ? "生产交接超过当前可信放行额度" : a.compareTo(r) != 0 ? "生产交接尚未全部实际接收；待接收数量 " + r.subtract(a)
                .toPlainString() : null;
        return new Summary(execution, q, r, a, q.effective().subtract(r), r.subtract(a), r.subtract(q.effective())
                .max(BigDecimal.ZERO), r.subtract(q.released())
                .max(BigDecimal.ZERO), completed, reason == null, reason);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesExecutionCompletionEntity complete(Long id, Command c) {
        if (c == null) ProductionClosureRules.bad();
        ProductionClosureRules.uuid(c.requestId());
        ProductionClosureRules.reason(c.reason());
        String hash = ProductionClosureRules.hash(id, c.expectedBasisHash(), c.reason(), ProductionClosureRules.actor());
        var execution = locks.execution(id);
        var existing = completions.selectByPrimaryKey(c.requestId());
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) conflict();
            return existing;
        }
        sources.execution(execution, null);
        if (integration.configuredMode(FactoryContext.required()) != Mode.LOCAL_ONLY)
            blocked("当前仅支持LOCAL完整完工，ERP未配置时不能假成功");
        var s = summary(id);
        if (s.completion() != null) blocked("执行已经正式完工，请查询或重放原完工请求");
        if (!s.canComplete()) blocked(s.blockedReason());
        if (c.expectedBasisHash() == null || !c.expectedBasisHash().equals(s.release().basisHash())) conflict();
        rules.checkReadyReferences(execution);
        var row = new MesExecutionCompletionEntity();
        row.setId(c.requestId());
        row.setExecutionId(id);
        row.setEffectiveQty(s.release().effective());
        row.setReleasedQty(s.release().released());
        row.setReceiptQty(s.received());
        row.setAcceptedQty(s.accepted());
        row.setBasisHash(s.release().basisHash());
        row.setRequestHash(hash);
        row.setOperatorUserId(ProductionClosureRules.actor());
        row.setRecordedAt(LocalDateTime.now());
        row.setReason(c.reason());
        try {
            if (completions.insert(row) != 1) conflict();
        } catch (DuplicateKeyException collision) {
            throw new FoundationException(ErrorCode.PRODUCTION_CLOSURE_CONFLICT, collision);
        }
        execution.setStatus(ProductionExecutionStatus.COMPLETED.getValue());
        execution.setEndTime(row.getRecordedAt());
        execution.setUpdateBy(row.getOperatorUserId().toString());
        execution.setUpdateTime(row.getRecordedAt());
        if (executions.updateByPrimaryKey(execution) != 1) conflict();
        return row;
    }
}
