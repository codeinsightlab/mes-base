package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.dao.ProductionReceiptQueryMapper;
import dev.mesbase.business.production.domain.MesProductionReceiptEntity;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Receive;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Reverse;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Query;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Summary;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Page;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Detail;
import dev.mesbase.business.production.domain.ProductionReceiptRow;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReceiptService {
    private final MesProductionReceiptMapper receipts;
    private final ProductionReceiptQueryMapper queries;
    private final ProductionQuantityService quantities;
    private final ProductionIdentityService identities;
    private final ProductionChainLocks locks;
    private final WmWarehouseMapper warehouses;
    private final BusinessDirectoryService directory;

    public ProductionReceiptService(MesProductionReceiptMapper receipts, ProductionReceiptQueryMapper queries,
                                    ProductionQuantityService quantities, ProductionIdentityService identities,
                                    ProductionChainLocks locks,
                                    WmWarehouseMapper warehouses, BusinessDirectoryService directory) {
        this.receipts = receipts;
        this.queries = queries;
        this.quantities = quantities;
        this.identities = identities;
        this.locks = locks;
        this.warehouses = warehouses;
        this.directory = directory;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Summary summary(Long executionId) {
        if (executionId == null) bad();
        var identity = identities.resolve("EXE:" + executionId);
        var q = quantities.getExecutionQuantities(executionId);
        var net = receipts.netQuantity(executionId);
        var terminal = q.nodes().isEmpty() ? null : q.nodes().getLast();
        boolean locked = ExecutionPathStatus.LOCKED.getValue().equals(q.pathStatus()) && terminal != null;
        BigDecimal output = locked ? terminal.effectiveOutputQty() : null;
        return new Summary(identity, q.routingLabel(), q.pathStatus(), terminal == null ? null : terminal.routingNodeId(),
                terminal == null ? null : terminal.name(), output, net, output == null ? null : output.subtract(net),
                output == null ? null : net.subtract(output).max(BigDecimal.ZERO), false,
                locked ? "RECEIPT_QUALITY_UNVERIFIED" : "RECEIPT_PATH_REQUIRED",
                locked ? ErrorCode.RECEIPT_QUALITY_UNVERIFIED.safeMessage() : "尚无已锁定的确切实际路径，无法确定交接产出");
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReceiptEntity receive(Receive c) {
        uuid(c.requestId());
        positive(c.quantity());
        if (c.executionId() == null || c.warehouseId() == null || c.productLotCode() != null && c.productLotCode()
                .length() > 100) bad();
        Long actor = SecurityUtils.getUserId();
        var existing = receipts.selectByPrimaryKey(c.requestId());
        if (existing != null) return replayReceive(existing, c, actor);
        var execution = locks.execution(c.executionId());
        existing = receipts.selectByPrimaryKey(c.requestId());
        if (existing != null) return replayReceive(existing, c, actor);
        if (!List.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.COMPLETED.getValue())
                .contains(execution.getStatus()))
            throw new FoundationException(ErrorCode.EXECUTION_INVALID);
        var q = quantities.getExecutionQuantities(c.executionId());
        if (!ExecutionPathStatus.LOCKED.getValue().equals(q.pathStatus()) || q.nodes().isEmpty())
            throw new FoundationException(ErrorCode.RECEIPT_PATH_REQUIRED);
        var warehouse = warehouses.selectByPrimaryKey(c.warehouseId());
        if (warehouse == null || !EnableStatus.ENABLED.getValue().equals(warehouse.getStatus()))
            throw new FoundationException(ErrorCode.RECEIPT_WAREHOUSE_INVALID);
        validateReceive(q.nodes().getLast().effectiveOutputQty(), receipts.netQuantity(c.executionId()), c.quantity());
        // 当前质量接口只有不良数量汇总，无稳定放行判断，且存量口径核验未完成。
        // 不以 qualityIntegrated=true 替代放行；不提供配置开关或测试专用生产旁路。
        requireQualityRelease();
        var row = new MesProductionReceiptEntity();
        row.setId(c.requestId());
        row.setReceiptNo("PR-" + c.requestId());
        row.setExecutionId(c.executionId());
        row.setWarehouseId(c.warehouseId());
        row.setProductLotCode(clean(c.productLotCode()));
        row.setRecordType("RECEIPT");
        row.setQuantityDelta(c.quantity());
        row.setOperatorUserId(actor);
        row.setRecordedBy(actor);
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        row.setOccurredAt(now);
        row.setCreateTime(now);
        return append(row);
    }

    private static void requireQualityRelease() {
        throw new FoundationException(ErrorCode.RECEIPT_QUALITY_UNVERIFIED);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesProductionReceiptEntity reverse(String id, Reverse c) {
        uuid(id);
        uuid(c.requestId());
        positive(c.quantity());
        if (clean(c.reason()) == null || c.reason().length() > 500) bad();
        Long actor = SecurityUtils.getUserId();
        var original = require(id);
        locks.execution(original.getExecutionId());
        var existing = receipts.selectByPrimaryKey(c.requestId());
        if (existing != null) {
            if (!"REVERSAL".equals(existing.getRecordType()) || !id.equals(existing.getReferenceReceiptId())
                    || !actor.equals(existing.getRecordedBy()) || existing.getQuantityDelta().negate()
                    .compareTo(c.quantity()) != 0
                    || !Objects.equals(existing.getReason(), clean(c.reason()))) conflict();
            return existing;
        }
        if (!"RECEIPT".equals(original.getRecordType()))
            throw new FoundationException(ErrorCode.RECEIPT_REFERENCE_INVALID);
        validateReverse(receipts.originalBalance(id), c.quantity());
        // 冲销只验证原始余额，不因当前质量未知、上游差额或仓库停用而阻止纠正事实。
        var row = new MesProductionReceiptEntity();
        row.setId(c.requestId());
        row.setReceiptNo("RV-" + c.requestId());
        row.setExecutionId(original.getExecutionId());
        row.setWarehouseId(original.getWarehouseId());
        row.setProductLotCode(original.getProductLotCode());
        row.setRecordType("REVERSAL");
        row.setQuantityDelta(c.quantity().negate());
        row.setReferenceReceiptId(id);
        row.setOperatorUserId(original.getOperatorUserId());
        row.setRecordedBy(actor);
        row.setOccurredAt(original.getOccurredAt());
        row.setCreateTime(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        row.setReason(clean(c.reason()));
        return append(row);
    }

    private MesProductionReceiptEntity append(MesProductionReceiptEntity row) {
        try {
            if (receipts.insert(row) != 1) conflict();
        } catch (DuplicateKeyException e) {
            throw new FoundationException(ErrorCode.RECEIPT_IDEMPOTENCY_CONFLICT, e);
        }
        return receipts.selectByPrimaryKey(row.getId());
    }

    private MesProductionReceiptEntity replayReceive(MesProductionReceiptEntity row, Receive c, Long actor) {
        if (!"RECEIPT".equals(row.getRecordType()) || !Objects.equals(row.getExecutionId(), c.executionId())
                || !Objects.equals(row.getWarehouseId(), c.warehouseId()) || !Objects.equals(row.getProductLotCode(), clean(c.productLotCode()))
                || row.getQuantityDelta()
                .compareTo(c.quantity()) != 0 || !actor.equals(row.getRecordedBy()) || !actor.equals(row.getOperatorUserId()))
            conflict();
        return row;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Page page(Query q) {
        ProductionQuantityService.paging(q.offset(), q.limit());
        if (q.productLotCode() != null && q.productLotCode().length() > 100) bad();
        if (q.referenceReceiptId() != null) uuid(q.referenceReceiptId());
        List<ProductionReceiptRow> rows;
        long total;
        try (var p = PageHelper.offsetPage(q.offset(), q.limit())) {
            rows = List.copyOf(queries.page(q));
            total = p.getTotal();
        }
        return new Page(rows, total, q.offset(), q.limit(), names(rows));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(String id) {
        uuid(id);
        var row = queries.detail(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return new Detail(row, receipts.originalBalance("RECEIPT".equals(row.getRecordType()) ? id : row.getReferenceReceiptId()), names(List.of(row)));
    }

    private Map<Long, String> names(List<ProductionReceiptRow> rows) {
        return directory.userLabels(rows.stream().flatMap(r -> Stream.of(r.getOperatorUserId(), r.getRecordedBy()))
                        .distinct().toList()).values().stream()
                .filter(l -> l.name() != null).collect(Collectors.toMap(l -> l.id(), l -> l.name()));
    }

    private MesProductionReceiptEntity require(String id) {
        var r = receipts.selectByPrimaryKey(id);
        if (r == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return r;
    }

    public static void validateReceive(BigDecimal output, BigDecimal received, BigDecimal quantity) {
        positive(quantity);
        if (quantity.compareTo(output.subtract(received)) > 0)
            throw new FoundationException(ErrorCode.RECEIPT_QUANTITY_CONFLICT);
    }

    public static void validateReverse(BigDecimal balance, BigDecimal quantity) {
        positive(quantity);
        if (quantity.compareTo(balance) > 0) throw new FoundationException(ErrorCode.RECEIPT_QUANTITY_CONFLICT);
    }

    private static void positive(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 6 || value.precision() - value.scale() > 14) bad();
    }

    private static void uuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) bad();
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void bad() {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static void conflict() {
        throw new FoundationException(ErrorCode.RECEIPT_IDEMPOTENCY_CONFLICT);
    }
}
