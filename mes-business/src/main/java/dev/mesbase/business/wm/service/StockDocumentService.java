package dev.mesbase.business.wm.service;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionReceiptExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import org.springframework.transaction.annotation.Propagation;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionCompletionService;
import org.springframework.beans.factory.ObjectProvider;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.wm.domain.StockDocumentModels.CancellationBasis;
import dev.mesbase.business.wm.domain.StockDocumentModels.Detail;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.uuid.IdUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * LOCAL document lifecycle; all stock mutations remain in LocalStockService and its caller's transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class StockDocumentService {
    private final WmStockDocumentMapper documents;
    private final WmStockDocumentLineMapper lines;
    private final WmInventoryMapper inventories;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final LocalStockService stock;
    private final BusinessSourceRules sources;
    private final ProductionChainLocks locks;
    private final MesProductionExecutionMapper executions;
    private final MesProductionReceiptMapper receipts;
    private final Validator validator;
    private final StockDocumentExtraMapper inbound;
    private final QualityReleaseService releases;
    private final WorkorderMapper orders;
    private final MesQualityTreatmentMapper qualityEvents;
    private final ObjectProvider<ProductionCompletionService> completion;

    public StockDocumentService(WmStockDocumentMapper documents, WmStockDocumentLineMapper lines,
                                WmInventoryMapper inventories, MaterialMapper materials, UnitMeasureMapper units,
                                LocalStockService stock, BusinessSourceRules sources, ProductionChainLocks locks,
                                MesProductionExecutionMapper executions, MesProductionReceiptMapper receipts,
                                Validator validator, StockDocumentExtraMapper inbound, QualityReleaseService releases,
                                WorkorderMapper orders, MesQualityTreatmentMapper qualityEvents,
                                ObjectProvider<ProductionCompletionService> completion) {
        this.completion = completion;
        this.qualityEvents = qualityEvents;
        this.orders = orders;
        this.inbound = inbound;
        this.releases = releases;
        this.documents = documents;
        this.lines = lines;
        this.inventories = inventories;
        this.materials = materials;
        this.units = units;
        this.stock = stock;
        this.sources = sources;
        this.locks = locks;
        this.executions = executions;
        this.receipts = receipts;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public StockDocumentModels.Page page(StockDocumentModels.Query q) {
        int offset = q.offset() == null ? 0 : q.offset();
        int limit = q.limit() == null ? 20 : q.limit();
        if (offset < 0 || limit < 1 || limit > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var f = new WmStockDocumentExample();
        var c = f.createCriteria();
        if (StringUtils.isNotBlank(q.documentType())) {
            type(q.documentType());
            c.andDocumentTypeEqualTo(q.documentType());
        }
        if (StringUtils.isNotBlank(q.status())) {
            if (!List.of(StockDocumentStatus.DRAFT.getValue(), StockDocumentStatus.POSTED.getValue(), StockDocumentStatus.CANCELLED.getValue())
                    .contains(q.status())) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            c.andStatusEqualTo(q.status());
        }
        if (StringUtils.isNotBlank(q.documentNo())) {
            if (q.documentNo().length() > 64) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            c.andDocumentNoLike("%" + q.documentNo() + "%");
        }
        f.setOrderByClause("id desc");
        try (var page = PageHelper.offsetPage(offset, limit)) {
            return new StockDocumentModels.Page(List.copyOf(documents.selectByExample(f)), page.getTotal(), offset, limit);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(Long id) {
        var row = require(id);
        StockDocumentModels.QualitySource qualitySource = null;
        var purpose = StockDocumentType.from(row.getDocumentType());
        if (purpose != null && purpose.isQualityReceipt() && row.getPostRequestId() != null) {
            var event = qualityEvents.selectByPrimaryKey(row.getPostRequestId());
            if (event != null && Objects.equals(id, event.getStockDocumentId()))
                qualitySource = new StockDocumentModels.QualitySource(event.getDeclarationId(), event.getId(), event.getExecutionId());
        }
        return new Detail(row, rows(id), qualitySource);
    }

    public Detail create(StockDocumentModels.Create command) {
        validate(command);
        if (type(command.documentType()).isQualityReceipt())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        return createDraft(command);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Detail createQualityReceipt(StockDocumentModels.Create command) {
        validate(command);
        if (!type(command.documentType()).isQualityReceipt() || command.sourceExecutionId() != null || command.sourceWorkorderId() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return createDraft(command);
    }

    private Detail createDraft(StockDocumentModels.Create command) {
        var purpose = type(command.documentType());
        if (purpose == StockDocumentType.PRODUCTION_RECEIPT && command.sourceExecutionId() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        sources.localCreation();
        if (command.sourceExecutionId() != null) {
            if (command.sourceWorkorderId() != null || purpose != StockDocumentType.PRODUCTION_RECEIPT)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            productionExecution(command.sourceExecutionId());
        }
        if (command.sourceWorkorderId() != null) {
            if (!StockDocumentType.OTHER_RECEIPT.getValue().equals(command.documentType()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            cancelled(command.sourceWorkorderId());
        }
        var row = new WmStockDocumentEntity();
        // Same server UUID numbering convention as ProductionReceipt; no center transaction in business writes.
        row.setDocumentNo("SD-" + IdUtils.randomUUID());
        row.setDocumentType(command.documentType());
        row.setStatus(StockDocumentStatus.DRAFT.getValue());
        row.setReason(command.reason());
        row.setRemark(command.remark());
        row.setSourceWorkorderId(command.sourceWorkorderId());
        row.setSourceExecutionId(command.sourceExecutionId());
        row.setRevision(0L);
        audit(row, true);
        if (documents.insert(row) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return detail(row.getId());
    }

    public Detail edit(Long id, StockDocumentModels.Edit command) {
        validate(command);
        var row = lock(id);
        draft(row, command.expectedRevision());
        sources.localCreation();
        row.setReason(command.reason());
        row.setRemark(command.remark());
        save(row);
        return detail(id);
    }

    public Detail cancel(Long id, StockDocumentModels.Version command) {
        validate(command);
        var row = lock(id);
        draft(row, command.expectedRevision());
        sources.localCreation();
        row.setStatus(StockDocumentStatus.CANCELLED.getValue());
        row.setCancelledBy(SecurityUtils.getUserId());
        row.setCancelledAt(LocalDateTime.now());
        save(row);
        return detail(id);
    }

    public WmStockDocumentLineEntity putLine(Long documentId, Long lineId, StockDocumentModels.Line command) {
        validate(command);
        var header = lock(documentId);
        draft(header, command.expectedRevision());
        sources.localCreation();
        var existing = rows(documentId);
        if (lineId == null && existing.size() >= 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var line = lineId == null ? new WmStockDocumentLineEntity() : requireLine(documentId, lineId);
        line.setDocumentId(documentId);
        if (lineId == null) {
            line.setLineNo(existing.stream().mapToInt(WmStockDocumentLineEntity::getLineNo).max().orElse(0) + 1);
            line.setRevision(0L);
        } else line.setRevision(line.getRevision() + 1);
        line.setStockId(command.stockId());
        line.setMaterialId(command.materialId());
        line.setWarehouseId(command.warehouseId());
        line.setUnitId(command.unitId());
        line.setBatchNo(command.batchNo());
        line.setBinCode(command.binCode());
        line.setQuantity(command.quantity());
        line.setRemark(command.remark());
        if (header.getSourceExecutionId() != null) ProductionQuantityInput.check(command.quantity());
        validateIdentity(line);
        if ((lineId == null ? lines.insert(line) : lines.updateByPrimaryKey(line)) != 1)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        save(header);
        return line;
    }

    public void removeLine(Long documentId, Long lineId, StockDocumentModels.Version command) {
        validate(command);
        var row = lock(documentId);
        draft(row, command.expectedRevision());
        sources.localCreation();
        requireLine(documentId, lineId);
        if (lines.deleteByPrimaryKey(lineId) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        save(row);
    }

    public Detail approve(Long id, StockDocumentModels.Post command) {
        validate(command);
        uuid(command.requestId());
        String hash = ProductionScanCode.digest(new ObjectMapper().writeValueAsString(
                List.of(id, command.requestId(), command.expectedRevision(), SecurityUtils.getUserId())));
        var before = require(id);
        // Original frozen replay is a read: a later factory mode cannot rewrite already committed LOCAL facts.
        if (replay(before, command, hash)) return detail(id);
        if (before.getSourceWorkorderId() != null) cancelled(before.getSourceWorkorderId());
        Long executionId = before.getSourceExecutionId();
        if (type(before.getDocumentType()).isQualityReceipt()) {
            var event = qualityEvents.selectByPrimaryKey(command.requestId());
            if (event == null || !Objects.equals(id, event.getStockDocumentId()))
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            executionId = event.getExecutionId();
        }
        if (executionId != null) completion.getObject().lockInboundChain(executionId);
        var row = lock(id);
        if (replay(row, command, hash)) return detail(id);
        draft(row, command.expectedRevision());
        sources.localCreation();
        var purpose = type(row.getDocumentType());
        if (!purpose.isApprovalAvailable()) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        var detail = rows(id);
        if (detail.isEmpty()) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        if (purpose.isQualityReceipt()) {
            var fact = qualityEvents.selectByPrimaryKey(command.requestId());
            if (fact == null || !Action.TRANSFER_WAREHOUSE.getValue().equals(fact.getActionType())
                    || !Objects.equals(id, fact.getStockDocumentId()) || detail.size() != 1
                    || !Objects.equals(detail.getFirst().getWarehouseId(), fact.getTargetWarehouseId())
                    || detail.getFirst().getQuantity().compareTo(fact.getTransferQty()) != 0)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        }
        if (row.getSourceWorkorderId() != null) {
            var basis = cancellationBasis(row.getSourceWorkorderId());
            BigDecimal amount = BigDecimal.ZERO;
            for (var line : detail) {
                if (!Objects.equals(basis.productId(), line.getMaterialId())
                        || StringUtils.isBlank(line.getRemark()))
                    ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
                amount = amount.add(line.getQuantity());
            }
            if (amount.compareTo(basis.remaining()) > 0)
                throw new FoundationException(ErrorCode.STOCK_DOCUMENT_BASIS_INVALID);
        }
        if (row.getSourceExecutionId() != null) {
            var basis = productionBasis(row.getSourceExecutionId());
            var execution = productionExecution(row.getSourceExecutionId());
            sources.finishedOutput(execution, null);
            sources.warehouses(BusinessSource.LOCAL.getValue(), detail.stream()
                    .map(WmStockDocumentLineEntity::getWarehouseId).toList());
            BigDecimal amount = BigDecimal.ZERO;
            for (var line : detail) {
                if (!Objects.equals(basis.productId(), line.getMaterialId()))
                    ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
                amount = amount.add(line.getQuantity());
            }
            if (!basis.canReceive() || amount.compareTo(basis.remaining()) > 0)
                throw new FoundationException(ErrorCode.STOCK_DOCUMENT_BASIS_INVALID);
        }
        // One deterministic inventory order for the complete document, including repeated-stock lines.
        var ordered = detail.stream().sorted(Comparator.comparing(WmStockDocumentLineEntity::getStockId)
                .thenComparing(WmStockDocumentLineEntity::getId)).toList();
        for (var line : ordered) stock.lock(line.getStockId());
        for (var line : ordered) validateIdentity(line);
        try {
            for (var line : ordered) {
                // First action uses original UUID: the existing action PK also serializes cross-document UUID reuse.
                String actionId = line.getId().equals(detail.getFirst().getId()) ? command.requestId()
                        : UUID.nameUUIDFromBytes((command.requestId() + ":" + line.getId()).getBytes(StandardCharsets.UTF_8))
                        .toString();
                var action = stock.postDocument(row.getDocumentType(), id, line, command.requestId(), actionId,
                        hash, row.getReason(), executionId);
                line.setStockActionId(action.getId());
                if (lines.updateByPrimaryKey(line) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
            }
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        row.setPostRequestId(command.requestId());
        row.setPostRequestHash(hash);
        row.setStatus(StockDocumentStatus.POSTED.getValue());
        row.setPostedBy(SecurityUtils.getUserId());
        row.setPostedAt(LocalDateTime.now());
        save(row);
        if (executionId != null) completion.getObject().afterInboundApproved(executionId, command.requestId());
        return detail(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public StockDocumentModels.ProductionBasis productionBasis(Long executionId) {
        var execution = executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var order = orders.selectByPrimaryKey(execution.getWorkorderId());
        boolean admitted = order != null && List.of(WorkOrderStatus.RELEASED.getValue(), WorkOrderStatus.COMPLETED.getValue())
                .contains(order.getStatus())
                && BusinessSource.LOCAL.getValue().equals(order.getSource()) && FactoryIntegrationContext.required()
                .mode() == FactoryRuntimeMode.LOCAL;
        var release = releases.state(executionId);
        var received = inbound.netProductionInbound(executionId);
        var remaining = release.released().min(release.effective()).subtract(received);
        String blocked = !admitted ? "当前工单状态、来源或模式不允许入库" : !List.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.COMPLETED.getValue())
                .contains(execution.getStatus())
                ? "执行状态不允许入库" : !release.safe() ? release.blockedReason() : !release.basisValid() ? "缺少当前质量依据下的正式放行"
                : remaining.signum() <= 0 ? "可入库数量不足或存在历史超额" : null;
        return new StockDocumentModels.ProductionBasis(executionId, execution.getProductId(), release.effective(), release.released(), received, remaining.max(BigDecimal.ZERO), blocked == null, blocked);
    }

    private MesProductionExecutionEntity productionExecution(Long id) {
        var execution = locks.execution(id);
        if (!List.of(ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.COMPLETED.getValue())
                .contains(execution.getStatus())) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        sources.finishedOutput(execution, null);
        return execution;
    }

    /**
     * Locks the original order and all its executions before reading net handover/acceptance facts.
     */
    public CancellationBasis cancellationBasis(Long workorderId) {
        var order = cancelled(workorderId);
        var e = new MesProductionExecutionExample();
        e.createCriteria().andWorkorderIdEqualTo(workorderId);
        e.setOrderByClause("id");
        var executionRows = bounded(() -> executions.selectByExample(e));
        for (var execution : executionRows) locks.execution(execution.getId());
        BigDecimal handed = BigDecimal.ZERO;
        BigDecimal accepted = inbound.netProductionInboundForOrder(workorderId);
        if (!executionRows.isEmpty()) {
            var ids = executionRows.stream().map(row -> row.getId()).toList();
            var r = new MesProductionReceiptExample();
            r.createCriteria().andExecutionIdIn(ids);
            for (var receipt : bounded(() -> receipts.selectByExample(r)))
                handed = handed.add(receipt.getQuantityDelta());

        }
        var d = new WmStockDocumentExample();
        d.createCriteria().andSourceWorkorderIdEqualTo(workorderId)
                .andStatusEqualTo(StockDocumentStatus.POSTED.getValue());
        var posted = bounded(() -> documents.selectByExample(d));
        BigDecimal manual = BigDecimal.ZERO;
        if (!posted.isEmpty()) {
            var l = new WmStockDocumentLineExample();
            l.createCriteria().andDocumentIdIn(posted.stream().map(WmStockDocumentEntity::getId).toList());
            for (var line : bounded(() -> lines.selectByExample(l))) manual = manual.add(line.getQuantity());
        }
        BigDecimal remaining = handed.subtract(accepted).subtract(manual).max(BigDecimal.ZERO);
        return new CancellationBasis(workorderId, order.getProductId(), order.getStatus(), handed, accepted, manual, remaining);
    }

    private WorkorderEntity cancelled(Long id) {
        var row = locks.order(id);
        if (!WorkOrderStatus.CANCELLED.getValue().equals(row.getStatus()) || !BusinessSource.LOCAL.getValue()
                .equals(row.getSource()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return row;
    }

    private void validateIdentity(WmStockDocumentLineEntity line) {
        sources.warehouse(BusinessSource.LOCAL.getValue(), line.getWarehouseId());
        var source = inventories.selectByPrimaryKey(line.getStockId());
        var material = materials.selectByPrimaryKey(line.getMaterialId());
        var unit = units.selectByPrimaryKey(line.getUnitId());
        if (source == null || material == null || unit == null
                || !EnableStatus.ENABLED.getValue().equals(source.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(material.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())
                || !Objects.equals(source.getMaterialId(), line.getMaterialId())
                || !Objects.equals(source.getWarehouseId(), line.getWarehouseId())
                || !Objects.equals(source.getBatchNo(), line.getBatchNo())
                || !Objects.equals(source.getBinCode(), line.getBinCode())
                || !Objects.equals(material.getUnitId(), line.getUnitId()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
    }

    private WmStockDocumentLineEntity requireLine(Long documentId, Long id) {
        var row = lines.selectByPrimaryKey(id);
        if (row == null || !Objects.equals(row.getDocumentId(), documentId))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return row;
    }

    private WmStockDocumentEntity lock(Long id) {
        if (id == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var row = documents.selectByPrimaryKeyForUpdate(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private WmStockDocumentEntity require(Long id) {
        if (id == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var row = documents.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private List<WmStockDocumentLineEntity> rows(Long id) {
        var f = new WmStockDocumentLineExample();
        f.createCriteria().andDocumentIdEqualTo(id);
        f.setOrderByClause("line_no,id");
        var found = bounded(() -> lines.selectByExample(f));
        if (found.size() > 100) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return found;
    }

    private static boolean replay(WmStockDocumentEntity row, StockDocumentModels.Post command, String hash) {
        if (!StockDocumentStatus.POSTED.getValue().equals(row.getStatus())) return false;
        if (!Objects.equals(row.getPostRequestId(), command.requestId())
                || !Objects.equals(row.getPostRequestHash(), hash))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return true;
    }

    private void save(WmStockDocumentEntity row) {
        row.setRevision(Math.addExact(row.getRevision(), 1));
        audit(row, false);
        if (documents.updateByPrimaryKey(row) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
    }

    private static void audit(WmStockDocumentEntity row, boolean create) {
        String actor = SecurityUtils.getUserId().toString();
        LocalDateTime now = LocalDateTime.now();
        if (create) {
            row.setCreateBy(actor);
            row.setCreateTime(now);
        }
        row.setUpdateBy(actor);
        row.setUpdateTime(now);
    }

    private void validate(Object command) {
        if (command == null || !validator.validate(command).isEmpty()) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static void draft(WmStockDocumentEntity row, Long revision) {
        if (!StockDocumentStatus.DRAFT.getValue()
                .equals(row.getStatus()) || !Objects.equals(row.getRevision(), revision))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
    }

    private static StockDocumentType type(String value) {
        var type = StockDocumentType.from(value);
        if (type == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return type;
    }

    private static void uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
    }

    private static <T> List<T> bounded(Supplier<List<T>> query) {
        try (var page = PageHelper.offsetPage(0, 10001, false)) {
            var rows = List.copyOf(query.get());
            if (rows.size() > 10000) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
            return rows;
        }
    }


}
