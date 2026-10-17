package dev.mesbase.business.wm.service;

import dev.mesbase.business.production.service.BusinessSourceRules;

import dev.mesbase.business.wm.dao.MesWarehouseAcceptanceMapper;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceExample;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesProductionReceiptEntity;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.production.domain.ProductionReceiptType;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import static dev.mesbase.business.production.domain.ProductionClosureRules.blocked;
import static dev.mesbase.business.production.domain.ProductionClosureRules.conflict;

/**
 * Physical warehouse acceptance is distinct from production handover and owns an atomic stock command.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WarehouseAcceptanceService {
    private final BusinessSourceRules sources;

    public record Accept(String requestId, Long stockId, BigDecimal quantity, String reason) {
    }

    public record Reverse(String requestId, BigDecimal quantity, String reason) {
    }

    public record State(MesProductionReceiptEntity receipt, Long productId, BigDecimal netHandover, BigDecimal accepted,
                        BigDecimal pending) {
    }

    public record Page(List<MesWarehouseAcceptanceEntity> items, long total, int offset, int limit) {
    }

    private final MesWarehouseAcceptanceMapper acceptances;
    private final MesProductionExecutionMapper executions;
    private final MesProductionReceiptMapper receipts;
    private final ProductionChainLocks locks;
    private final LocalStockService stock;
    private final QualityReleaseService releases;
    private final WarehouseIntegration integration;

    public WarehouseAcceptanceService(MesWarehouseAcceptanceMapper acceptances, MesProductionReceiptMapper receipts,
                                      ProductionChainLocks locks, LocalStockService stock,
                                      QualityReleaseService releases, WarehouseIntegration integration,
                                      MesProductionExecutionMapper executions, BusinessSourceRules sources) {
        this.sources = sources;
        this.executions = executions;
        this.acceptances = acceptances;
        this.receipts = receipts;
        this.locks = locks;
        this.stock = stock;
        this.releases = releases;
        this.integration = integration;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public State state(String receiptId) {
        var r = receipt(receiptId);
        var a = acceptances.netReceipt(receiptId);
        var net = receipts.originalBalance(receiptId);
        return new State(r, executions.selectByPrimaryKey(r.getExecutionId()).getProductId(), net, a, net.subtract(a));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Page history(String receiptId, int offset, int limit) {
        ProductionClosureRules.uuid(receiptId);
        devPaging(offset, limit);
        var f = new MesWarehouseAcceptanceExample();
        f.createCriteria().andReceiptIdEqualTo(receiptId);
        f.setOrderByClause("recorded_at DESC,id DESC");
        try (var p = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(acceptances.selectByExample(f));
            return new Page(rows, p.getTotal(), offset, limit);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesWarehouseAcceptanceEntity accept(String receiptId, Accept c) {
        if (c == null) ProductionClosureRules.bad();
        ProductionClosureRules.uuid(c.requestId());
        ProductionClosureRules.quantity(c.quantity(), false);
        ProductionClosureRules.reason(c.reason());
        if (c.stockId() == null) ProductionClosureRules.bad();
        var r = receipt(receiptId);
        var execution = locks.execution(r.getExecutionId());
        String hash = ProductionClosureRules.hash(receiptId, c.stockId(), ProductionClosureRules.number(c.quantity()), c.reason(), ProductionClosureRules.actor());
        var old = replay(c.requestId(), hash);
        if (old != null) return old;
        sources.execution(execution, r.getWarehouseId());
        local();
        if (!ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus()))
            blocked("仅运行中的执行允许新增仓储接收");
        var q = releases.state(r.getExecutionId());
        var handed = receipts.netQuantity(r.getExecutionId());
        if (!q.basisValid() || !q.safe() || handed.compareTo(q.released()) > 0 || handed.compareTo(q.effective()) > 0)
            blocked("当前质量放行失效或存在历史超交差额，暂停接收并核对原事实");
        if (c.quantity().compareTo(receipts.originalBalance(receiptId).subtract(acceptances.netReceipt(receiptId))) > 0)
            blocked("实际接收超过该交接当前待接收数量");
        var fact = fact(c.requestId(), r, execution.getProductId(), c.stockId(), c.quantity(), null, hash, c.reason());
        append(fact, r, execution.getProductId());
        return fact;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesWarehouseAcceptanceEntity reverse(String originalId, Reverse c) {
        if (c == null) ProductionClosureRules.bad();
        ProductionClosureRules.uuid(originalId);
        ProductionClosureRules.uuid(c.requestId());
        ProductionClosureRules.quantity(c.quantity(), false);
        ProductionClosureRules.reason(c.reason());
        var original = acceptances.selectByPrimaryKey(originalId);
        if (original == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var execution = locks.execution(original.getExecutionId());
        String hash = ProductionClosureRules.hash(originalId, ProductionClosureRules.number(c.quantity()), c.reason(), ProductionClosureRules.actor());
        var old = replay(c.requestId(), hash);
        if (old != null) return old;
        if (ProductionExecutionStatus.COMPLETED.getValue().equals(execution.getStatus()))
            blocked("已正式完工；不支持终态接收逆向，请保留历史差额并走专项纠正");
        if (original.getQuantityDelta().signum() <= 0 || c.quantity()
                .compareTo(acceptances.originalBalance(originalId)) > 0) conflict();
        var r = receipt(original.getReceiptId());
        sources.frozenLocal(execution.getWorkorderId(), r.getWarehouseId());
        var fact = fact(c.requestId(), r, original.getProductId(), original.getStockId(), c.quantity()
                .negate(), originalId, hash, c.reason());
        append(fact, r, original.getProductId());
        return fact;
    }

    private void append(MesWarehouseAcceptanceEntity fact, MesProductionReceiptEntity receipt, Long productId) {
        try {
            stock.receiveProduct(fact, receipt, productId);
            if (acceptances.insert(fact) != 1) conflict();
        } catch (DuplicateKeyException collision) {
            throw new FoundationException(ErrorCode.PRODUCTION_CLOSURE_CONFLICT, collision);
        }
    }

    private MesWarehouseAcceptanceEntity fact(String id, MesProductionReceiptEntity r, Long product, Long stockId,
                                              BigDecimal delta, String reference, String hash, String reason) {
        var f = new MesWarehouseAcceptanceEntity();
        f.setId(id);
        f.setReceiptId(r.getId());
        f.setExecutionId(r.getExecutionId());
        f.setProductId(product);
        f.setStockId(stockId);
        f.setQuantityDelta(delta);
        f.setReferenceId(reference);
        f.setStockActionId(id);
        f.setRequestHash(hash);
        f.setOperatorUserId(ProductionClosureRules.actor());
        f.setRecordedAt(LocalDateTime.now());
        f.setReason(reason);
        return f;
    }

    private MesWarehouseAcceptanceEntity replay(String id, String hash) {
        var old = acceptances.selectByPrimaryKey(id);
        if (old != null && !hash.equals(old.getRequestHash())) conflict();
        return old;
    }

    private MesProductionReceiptEntity receipt(String id) {
        ProductionClosureRules.uuid(id);
        var r = receipts.selectByPrimaryKey(id);
        if (r == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (!ProductionReceiptType.RECEIPT.getValue().equals(r.getRecordType())) conflict();
        return r;
    }

    private void local() {
        if (integration.configuredMode(FactoryContext.required()) != Mode.LOCAL_ONLY)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }

    private static void devPaging(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) ProductionClosureRules.bad();
    }
}
