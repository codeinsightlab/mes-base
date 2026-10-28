package dev.mesbase.business.wm.service;

import dev.mesbase.business.quality.domain.MesQualityTreatmentExample;

import dev.mesbase.common.utils.RequestFingerprint;
import tools.jackson.core.JacksonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ExecutionPathService;

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
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import org.springframework.transaction.annotation.Propagation;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import org.springframework.beans.factory.ObjectProvider;
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
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import jakarta.validation.Validator;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private static final Logger LOG = LoggerFactory.getLogger(StockDocumentService.class);
    private final WmStockDocumentMapper documents;
    private final ObjectProvider<ProductionInputService> productionInputs;
    private final ObjectProvider<MaterialIssueInputFacts> inputFacts;
    private final ExecutionPathService executionPaths;
    private final WmStockDocumentLineMapper lines;
    private final WmInventoryMapper inventories;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final MaterialQuantityRules quantityRules;
    private final LocalStockService stock;
    private final BusinessSourceRules sources;
    private final ProductionChainLocks locks;
    private final MesProductionExecutionMapper executions;
    private final MesProductionReceiptMapper receipts;
    private final Validator validator;
    private final StockDocumentExtraMapper inbound;
    private final ProductionQuantityService quantities;
    private final QualityTreatmentService quality;
    private final WorkorderMapper orders;
    private final MesQualityTreatmentMapper qualityEvents;
    private final ObjectProvider<ProductionCompletionService> completion;
    private final AutoCodeNumbering autoCodes;
    private final TransactionTemplate businessTransactions;

    public StockDocumentService(WmStockDocumentMapper documents, WmStockDocumentLineMapper lines,
                                WmInventoryMapper inventories, MaterialMapper materials, UnitMeasureMapper units,
                                LocalStockService stock, BusinessSourceRules sources, ProductionChainLocks locks,
                                MesProductionExecutionMapper executions, MesProductionReceiptMapper receipts,
                                Validator validator, StockDocumentExtraMapper inbound,
                                ProductionQuantityService quantities, QualityTreatmentService quality,
                                WorkorderMapper orders, MesQualityTreatmentMapper qualityEvents,
                                ObjectProvider<ProductionCompletionService> completion,
                                ObjectProvider<ProductionInputService> productionInputs,
                                ObjectProvider<MaterialIssueInputFacts> inputFacts,
                                ExecutionPathService executionPaths, MaterialQuantityRules quantityRules,
                                AutoCodeNumbering autoCodes,
                                @Qualifier("businessTransactionManager") PlatformTransactionManager businessManager) {
        this.autoCodes = autoCodes;
        this.businessTransactions = new TransactionTemplate(businessManager);
        this.businessTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.productionInputs = productionInputs;
        this.inputFacts = inputFacts;
        this.executionPaths = executionPaths;
        this.completion = completion;
        this.qualityEvents = qualityEvents;
        this.orders = orders;
        this.inbound = inbound;
        this.quantities = quantities;
        this.quality = quality;
        this.documents = documents;
        this.lines = lines;
        this.inventories = inventories;
        this.materials = materials;
        this.units = units;
        this.quantityRules = quantityRules;
        this.stock = stock;
        this.sources = sources;
        this.locks = locks;
        this.executions = executions;
        this.receipts = receipts;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public StockDocumentModels.Page page(StockDocumentModels.Query q) {
        if (StockDocumentDirection.from(q.direction()) == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        int offset = q.offset() == null ? 0 : q.offset();
        int limit = q.limit() == null ? 20 : q.limit();
        if (offset < 0 || limit < 1 || limit > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var f = new WmStockDocumentExample();
        var c = f.createCriteria();
        if (StringUtils.isNotBlank(q.documentType())) {
            var selectedType = type(q.documentType());
            if (selectedType.getDirection().getValue().equals(q.direction()) == false)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            c.andDocumentTypeEqualTo(q.documentType());
        }
        c.andDirectionEqualTo(q.direction());
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
            return new StockDocumentModels.Page(List.copyOf(documents.selectByExampleWithBLOBs(f)), page.getTotal(), offset, limit);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(Long id) {
        var row = require(id);
        requireDirection(row.getDirection(), type(row.getDocumentType()));
        StockDocumentModels.QualitySource qualitySource = null;
        var purpose = StockDocumentType.from(row.getDocumentType());
        if (purpose != null && purpose.isQualityReceipt() && row.getPostRequestId() != null) {
            var event = qualityEvents.selectByPrimaryKey(row.getPostRequestId());
            if (event != null && Objects.equals(id, event.getStockDocumentId()))
                qualitySource = new StockDocumentModels.QualitySource(event.getDeclarationId(), event.getId(), event.getExecutionId());
        }
        StockDocumentModels.MaterialIssueBasis materialIssueBasis = null;
        String materialIssueBasisError = null;
        if (purpose == StockDocumentType.MATERIAL_ISSUE && row.getSourceRequestJson() != null) {
            try {
                var request = new ObjectMapper().readValue(row.getSourceRequestJson(), ProductionInputService.Command.class);
                if (request == null || request.requestId() == null || request.code() == null
                        || request.workorderId() == null || request.outputQty() == null || request.routingId() == null
                        || request.routingNodeIds() == null || request.routingNodeIds().isEmpty()) {
                    LOG.warn("Material issue draft basis lacks required fields; document {}", id);
                    materialIssueBasisError = "领料投入依据缺失，原单据及明细仍可查看，暂不能审核";
                } else {
                    materialIssueBasis = new StockDocumentModels.MaterialIssueBasis(request.requestId(), request.code(),
                            request.workorderId(), request.outputQty(), request.routingId(), request.routingNodeIds());
                }
            } catch (JacksonException failure) {
                LOG.warn("Material issue draft basis cannot be read; document {}", id, failure);
                materialIssueBasisError = "领料投入依据读取失败，原单据及明细仍可查看，暂不能审核";
            }
        }
        return new Detail(row, rows(id), qualitySource, materialIssueBasis, materialIssueBasisError);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ProductionInputBasis productionInputBasis(Long documentId) {
        var document = require(documentId);
        if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(document.getDocumentType()))
            throw new FoundationException(ErrorCode.NOT_FOUND);
        if (document.getProductionRequestId() == null) return null;
        var snapshot = inputFacts.getObject().snapshot(documentId);
        var calculation = snapshot.calculation();
        List<Long> selectedNodes = List.of();
        String pathError = null;
        if (document.getSourceExecutionId() != null) {
            try {
                selectedNodes = executionPaths.view(document.getSourceExecutionId()).selectedNodeIds();
            } catch (FoundationException failure) {
                pathError = "实际路径读取失败；领料投入与库存明细仍可查看";
            }
        }
        var bom = calculation.basis();
        return new ProductionInputBasis(document.getProductionRequestId(), document.getProductionInputKind(),
                document.getProductionSourceMode(), document.getSourceExecutionId(),
                calculation.order().workorderId(), calculation.order().workorderCode(),
                document.getProductionOutputQty(), calculation.issueQty(), calculation.stock().materialId(),
                calculation.stock().materialCode(), calculation.stock().warehouseId(),
                calculation.stock().unitId(), calculation.stock().lotCode(),
                bom == null ? null : bom.bomId(), bom == null ? null : bom.baseQty(),
                bom == null ? null : bom.componentQty(), document.getProductionPathRevision(), selectedNodes,
                document.getProductionBusinessOccurredAt(), document.getProductionConfirmedAt(),
                document.getProductionStockApplied(), document.getProductionVoidOperationId(),
                document.getProductionVoidResultStatus(), pathError);
    }

    public record ProductionInputBasis(String requestId, String inputKind, String sourceMode,
                                       Long executionId, Long workorderId, String workorderCode,
                                       BigDecimal outputQty, BigDecimal issueQty, Long materialId,
                                       String materialCode, Long warehouseId, Long unitId, String batchNo,
                                       Long bomId, BigDecimal bomBaseQty, BigDecimal bomComponentQty,
                                       Integer pathRevision, List<Long> routingNodeIds,
                                       LocalDateTime businessOccurredAt,
                                       LocalDateTime confirmedAt, Boolean stockApplied,
                                       String voidOperationId, String voidResultStatus, String pathError) { }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public Detail create(StockDocumentModels.Create command) {
        validate(command);
        StockDocumentType purpose = type(command.documentType());
        if (purpose.isQualityReceipt() || purpose == StockDocumentType.MATERIAL_ISSUE)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        requireDirection(command.direction(), purpose);
        String category = numberingCategory(command.direction());
        String ruleCode = command.ruleCode() == null || command.ruleCode().isBlank()
                ? purpose.getValue() : command.ruleCode();
        if (!purpose.getValue().equals(ruleCode)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        String documentNo = autoCodes.getBusinessDocNo(ruleCode, category);
        return businessTransactions.execute(status -> createDraft(command, documentNo));
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public Detail createAggregate(StockDocumentModels.AggregateSave command) {
        validateAggregate(command, true);
        var header = command.header();
        StockDocumentType purpose = type(header.documentType());
        requireDirection(header.direction(), purpose);
        if (purpose == StockDocumentType.MATERIAL_ISSUE)
            return productionInputs.getObject().createAggregateDraft(command);
        if (purpose.isQualityReceipt())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        String category = numberingCategory(header.direction());
        if (header.ruleCode() == null || !header.documentType().equals(header.ruleCode()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        String documentNo = autoCodes.getBusinessDocNo(header.ruleCode(), category);
        return businessTransactions.execute(status -> {
            var draft = createDraft(new StockDocumentModels.Create(header.direction(), header.documentType(), header.reason(),
                    header.remark(), header.sourceWorkorderId(), header.sourceExecutionId(), header.ruleCode()), documentNo);
            Long id = draft.document().getId();
            for (StockDocumentModels.AggregateLine item : command.items()) {
                var latest = require(id);
                putLine(id, null, lineCommand(latest.getRevision(), item));
            }
            return detail(id);
        });
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public Detail updateAggregate(Long id, StockDocumentModels.AggregateSave command) {
        validateAggregate(command, false);
        var header = command.header();
        var current = require(id);
        if (type(current.getDocumentType()) == StockDocumentType.MATERIAL_ISSUE
                || current.getSourceRequestJson() != null
                || !Objects.equals(current.getDocumentType(), header.documentType())
                || !Objects.equals(current.getDirection(), header.direction()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        edit(id, new StockDocumentModels.Edit(header.expectedRevision(), header.reason(), header.remark()));
        var existing = rows(id);
        var retained = new HashSet<Long>();
        for (StockDocumentModels.AggregateLine item : command.items()) {
            if (item.id() != null && !retained.add(item.id())) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
            var latest = require(id);
            putLine(id, item.id(), lineCommand(latest.getRevision(), item));
        }
        for (WmStockDocumentLineEntity line : existing) {
            if (!retained.contains(line.getId())) {
                var latest = require(id);
                removeLine(id, line.getId(), new StockDocumentModels.Version(latest.getRevision()));
            }
        }
        return detail(id);
    }

    private static StockDocumentModels.Line lineCommand(Long revision, StockDocumentModels.AggregateLine item) {
        return new StockDocumentModels.Line(revision, item.stockId(), item.materialId(), item.warehouseId(),
                item.unitId(), item.batchNo(), item.binCode(), item.quantity(), item.remark());
    }

    private void validateAggregate(StockDocumentModels.AggregateSave command, boolean creating) {
        if (command == null || command.header() == null || command.items() == null || command.items().isEmpty()
                || command.items().size() > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (creating && command.header().expectedRevision() != null
                || !creating && command.header().expectedRevision() == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        validate(new StockDocumentModels.Create(command.header().direction(), command.header().documentType(), command.header().reason(),
                command.header().remark(), command.header().sourceWorkorderId(), command.header().sourceExecutionId(),
                command.header().ruleCode()));
        requireDirection(command.header().direction(), type(command.header().documentType()));
        for (StockDocumentModels.AggregateLine item : command.items()) {
            if (item == null || item.stockId() == null || item.materialId() == null || item.warehouseId() == null
                    || item.unitId() == null || item.quantity() == null || item.quantity().signum() <= 0
                    || item.id() != null && creating)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Detail createQualityReceipt(StockDocumentModels.Create command, String documentNo) {
        validate(command);
        if (!type(command.documentType()).isQualityReceipt() || command.sourceExecutionId() != null || command.sourceWorkorderId() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return createDraft(command, documentNo);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public Detail createWithNumber(StockDocumentModels.Create command, String documentNo) {
        validate(command);
        StockDocumentType purpose = type(command.documentType());
        requireDirection(command.direction(), purpose);
        if (purpose.isQualityReceipt() || purpose == StockDocumentType.MATERIAL_ISSUE)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        return createDraft(command, documentNo);
    }

    private Detail createDraft(StockDocumentModels.Create command, String documentNo) {
        var purpose = type(command.documentType());
        requireDirection(command.direction(), purpose);
        if (purpose == StockDocumentType.PRODUCTION_RECEIPT && command.sourceExecutionId() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        sources.localCreation();
        if (command.sourceExecutionId() != null) {
            if (purpose == StockDocumentType.PRODUCTION_RECEIPT) {
                if (command.sourceWorkorderId() != null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
                productionExecution(command.sourceExecutionId());
            } else if (purpose != StockDocumentType.MATERIAL_ISSUE) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
        if (command.sourceWorkorderId() != null) {
            if (purpose == StockDocumentType.MATERIAL_ISSUE) sources.order(command.sourceWorkorderId());
            else {
                if (purpose != StockDocumentType.OTHER_RECEIPT) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
                cancelled(command.sourceWorkorderId());
            }
        }
        var row = new WmStockDocumentEntity();
        if (documentNo == null || documentNo.isBlank()) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        row.setDocumentNo(documentNo);
        row.setDocumentType(command.documentType());
        row.setDirection(command.direction());
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
        if (header.getSourceRequestJson() != null) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var existing = rows(documentId);
        if (lineId == null && existing.size() >= 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var line = lineId == null ? new WmStockDocumentLineEntity() : requireLine(documentId, lineId);
        boolean unchanged = lineId != null && Objects.equals(line.getMaterialId(), command.materialId())
                && Objects.equals(line.getStockId(), command.stockId()) && Objects.equals(line.getUnitId(), command.unitId())
                && line.getQuantity().compareTo(command.quantity()) == 0;
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
        validateIdentity(line, settlement(header, line), !unchanged);
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
        if (row.getSourceRequestJson() != null) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        requireLine(documentId, lineId);
        if (lines.deleteByPrimaryKey(lineId) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        save(row);
    }

    /** Lock the unified issue header after its work order and execution locks have been acquired. */
    public WmStockDocumentEntity lockMaterialIssueForVoid(Long documentId) {
        var row = lock(documentId);
        if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(row.getDocumentType()))
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return row;
    }

    public Detail approve(Long id, StockDocumentModels.Post command) {
        return approve(id, command, false);
    }

    private Detail approve(Long id, StockDocumentModels.Post command, boolean productionCommand) {
        validate(command);
        uuid(command.requestId());
        String hash = RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(
                List.of(id, command.requestId(), command.expectedRevision(), SecurityUtils.getUserId())));
        var before = require(id);
        var purposeBefore = type(before.getDocumentType());
        if (purposeBefore == StockDocumentType.MATERIAL_ISSUE && !productionCommand
                && StockDocumentStatus.DRAFT.getValue().equals(before.getStatus())) {
            productionInputs.getObject().requireApprovalPermissions();
            return productionInputs.getObject().approveDraft(id, command);
        }
        // Original frozen replay is a read: a later factory mode cannot rewrite already committed LOCAL facts.
        if (replay(before, command, hash)) return detail(id);
        if (before.getSourceWorkorderId() != null && purposeBefore != StockDocumentType.MATERIAL_ISSUE)
            cancelled(before.getSourceWorkorderId());
        Long executionId = before.getSourceExecutionId();
        if (type(before.getDocumentType()).isQualityReceipt()) {
            var event = qualityEvents.selectByPrimaryKey(command.requestId());
            if (event == null || !Objects.equals(id, event.getStockDocumentId()))
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            executionId = event.getExecutionId();
        }
        if (executionId != null && purposeBefore != StockDocumentType.MATERIAL_ISSUE)
            completion.getObject().lockInboundChain(executionId);
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
        if (purpose == StockDocumentType.MATERIAL_ISSUE) validateMaterialIssue(row, detail, command);
        if (row.getSourceWorkorderId() != null && purpose != StockDocumentType.MATERIAL_ISSUE) {
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
        if (row.getSourceExecutionId() != null && purpose != StockDocumentType.MATERIAL_ISSUE) {
            var basis = productionBasis(row.getSourceExecutionId());
            var execution = productionExecution(row.getSourceExecutionId());
            sources.finishedOutput(execution, null);
            sources.warehouses(BusinessSource.LOCAL.getValue(), detail.stream()
                    .map(WmStockDocumentLineEntity::getWarehouseId).toList());
            BigDecimal amount = BigDecimal.ZERO;
            for (var line : detail) {
                if (!Objects.equals(basis.productId(), line.getMaterialId())
                        || StringUtils.isBlank(execution.getBatchNo())
                        || !execution.getBatchNo().equals(line.getBatchNo()))
                    ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
                amount = amount.add(line.getQuantity());
            }
            if (!basis.canReceive() || amount.compareTo(basis.remaining()) > 0)
                throw new FoundationException(ErrorCode.STOCK_DOCUMENT_BASIS_INVALID);
        }
        // One deterministic inventory order for the complete document, including repeated-stock lines.
        var ordered = detail.stream().sorted(Comparator.comparing(WmStockDocumentLineEntity::getStockId)
                .thenComparing(WmStockDocumentLineEntity::getId)).toList();
        quantityRules.lockForWrite(ordered.stream().map(WmStockDocumentLineEntity::getMaterialId).toList());
        for (var line : ordered) validateIdentity(line, settlement(row, line), true);
        for (var line : ordered) stock.lock(line.getStockId());
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
        if (executionId != null && purpose != StockDocumentType.MATERIAL_ISSUE)
            completion.getObject().afterInboundApproved(executionId, command.requestId());
        return detail(id);
    }

    /**
     * Creates only the frozen request and its formal warehouse draft; no production facts are written.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public Detail createMaterialIssueDraft(ProductionInputService.Command command, Calculation calculation,
                                          String documentNo) {
        String frozen = new ObjectMapper().writeValueAsString(command);
        if (frozen.length() > 8000) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var existing = findMaterialIssueDraft(command);
        if (existing != null) return existing;
        var draft = materialIssueDocument(calculation, documentNo);
        var row = lock(draft.document().getId());
        row.setSourceRequestJson(frozen);
        row.setPostRequestId(command.requestId());
        row.setProductionRequestId(command.requestId());
        row.setProductionRequestHash(RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(command)));
        row.setProductionInputKind(command.inputKind());
        row.setProductionOutputQty(command.outputQty());
        row.setProductionSourceMode(calculation.sourceMode());
        row.setProductionCalculationJson(new ObjectMapper().writeValueAsString(calculation));
        row.setProductionRequestCreatedAt(LocalDateTime.now());
        row.setProductionStockApplied(false);
        try {
            if (documents.updateByPrimaryKeyWithBLOBs(row) != 1)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, conflict);
        }
        return detail(row.getId());
    }

    public Detail findMaterialIssueDraft(ProductionInputService.Command command) {
        var filter = new WmStockDocumentExample();
        filter.createCriteria().andPostRequestIdEqualTo(command.requestId());
        var existing = bounded(() -> documents.selectByExampleWithBLOBs(filter));
        if (existing.isEmpty()) return null;
        if (existing.size() != 1)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        try {
            var saved = new ObjectMapper().readValue(existing.getFirst().getSourceRequestJson(),
                    ProductionInputService.Command.class);
            if (!Objects.equals(saved, command)) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        } catch (JacksonException failure) {
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, failure);
        }
        return detail(existing.getFirst().getId());
    }

    /** Finds the original request identity before recalculating against mutable stock or production state. */
    public Detail findMaterialIssueDraftByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var filter = new WmStockDocumentExample();
        filter.createCriteria().andPostRequestIdEqualTo(requestId);
        var existing = bounded(() -> documents.selectByExampleWithBLOBs(filter));
        if (existing.isEmpty()) return null;
        if (existing.size() != 1 || type(existing.getFirst().getDocumentType()) != StockDocumentType.MATERIAL_ISSUE)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return detail(existing.getFirst().getId());
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public String findDocumentIdForRequest(String requestId) {
        if (requestId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var filter = new WmStockDocumentExample();
        filter.createCriteria().andProductionRequestIdEqualTo(requestId);
        var found = bounded(() -> documents.selectByExampleWithBLOBs(filter));
        if (found.size() > 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return found.isEmpty() ? null : found.getFirst().getId().toString();
    }

    private Detail materialIssueDocument(Calculation calculation, String documentNo) {
        var doc = createDraft(new StockDocumentModels.Create(StockDocumentDirection.OUT.getValue(), StockDocumentType.MATERIAL_ISSUE.getValue(),
                "正式生产领料出库", null, calculation.order().workorderId(), null,
                StockDocumentType.MATERIAL_ISSUE.getValue()), documentNo);
        var source = calculation.stock();
        putLine(doc.document().getId(), null, new StockDocumentModels.Line(doc.document().getRevision(),
                Long.valueOf(source.reference()), source.materialId(), source.warehouseId(), source.unitId(),
                source.lotCode(), source.binCode(), calculation.issueQty(), null));
        return detail(doc.document().getId());
    }

    /**
     * Approval has already obtained the order lock; binding cannot be supplied by a client.
     */
    public void bindMaterialIssue(Long documentId, WmStockDocumentEntity input, Calculation calculation) {
        var row = lock(documentId);
        if (type(row.getDocumentType()) != StockDocumentType.MATERIAL_ISSUE
                || !StockDocumentStatus.DRAFT.getValue().equals(row.getStatus())
                || !Objects.equals(row.getSourceWorkorderId(), calculation.order().workorderId())
                || !Objects.equals(row.getProductionRequestId(), input.getProductionRequestId()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        row.setSourceExecutionId(input.getSourceExecutionId());
        if (documents.updateByPrimaryKeyWithBLOBs(row) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
    }

    /**
     * The existing issue confirmation enters the same owner and approval algorithm as the Web page.
     */
    public void postMaterialIssue(Long documentId, WmStockDocumentEntity input,
                                  Calculation calculation) {
        productionInputs.getObject().requireApprovalPermissions();
        if (!Objects.equals(input.getId(), documentId))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        approve(documentId, new StockDocumentModels.Post(input.getProductionRequestId(), require(documentId).getRevision()), true);
    }

    private void validateMaterialIssue(WmStockDocumentEntity document, List<WmStockDocumentLineEntity> detail,
                                       StockDocumentModels.Post command) {
        var input = inputFacts.getObject().find(document.getId());
        if (input == null || !Objects.equals(input.getProductionRequestId(), command.requestId())
                || !Objects.equals(input.getSourceExecutionId(), document.getSourceExecutionId()) || detail.size() != 1)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        var calculation = inputFacts.getObject().snapshot(document.getId()).calculation();
        var line = detail.getFirst();
        var source = calculation.stock();
        if (!Objects.equals(source.reference(), line.getStockId().toString())
                || !Objects.equals(calculation.order().workorderId(), document.getSourceWorkorderId())
                || !Objects.equals(source.materialId(), line.getMaterialId())
                || !Objects.equals(source.warehouseId(), line.getWarehouseId())
                || !Objects.equals(source.unitId(), line.getUnitId())
                || !Objects.equals(source.lotCode(), line.getBatchNo())
                || !Objects.equals(source.binCode(), line.getBinCode())
                || calculation.issueQty().compareTo(line.getQuantity()) != 0)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
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
        var projection = quantities.getExecutionQuantities(executionId);
        var basis = quality.productionBasis(executionId);
        var effective = projection.nodes().isEmpty() ? BigDecimal.ZERO : projection.nodes().getLast()
                .effectiveOutputQty();
        var received = inbound.netProductionInbound(executionId);
        var remaining = effective.subtract(received);
        String blocked = null;
        if (!admitted) blocked = "当前工单状态、来源或模式不允许入库";
        else if (!ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus()))
            blocked = "执行状态不允许入库";
        else if (!ExecutionPathStatus.LOCKED.getValue().equals(projection.pathStatus()) || projection.nodes().isEmpty())
            blocked = "实际生产路径尚未锁定";
        else if (projection.confirmedInputQty().signum() <= 0 || projection.reservedInputQty().signum() > 0)
            blocked = "缺少正式投入或仍有未决投产意图";
        else if (projection.nodes().stream().anyMatch(node -> node.unverifiedDefectQty().signum() > 0)
                || basis.declarations().stream()
                .anyMatch(declaration -> declaration.attribution() == Attribution.UNVERIFIED))
            blocked = "存在待核实不良，不能审核生产入库";
        else if (remaining.signum() <= 0) blocked = "可入库数量不足或存在数量超额";
        return new StockDocumentModels.ProductionBasis(executionId, execution.getProductId(), effective,
                received, remaining.max(BigDecimal.ZERO), blocked == null, blocked);
    }

    private MesProductionExecutionEntity productionExecution(Long id) {
        var execution = locks.execution(id);
        if (!ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
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
        var posted = bounded(() -> documents.selectByExampleWithBLOBs(d));
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

    private BigDecimal settlement(WmStockDocumentEntity header, WmStockDocumentLineEntity line) {
        if (type(header.getDocumentType()) == StockDocumentType.PRODUCTION_RECEIPT && header.getSourceExecutionId() != null) {
            var basis = productionBasis(header.getSourceExecutionId());
            if (Objects.equals(basis.productId(), line.getMaterialId())) return basis.remaining();
        }
        if (type(header.getDocumentType()).isQualityReceipt()) {
            var filter = new MesQualityTreatmentExample();
            filter.createCriteria().andStockDocumentIdEqualTo(header.getId());
            var facts = qualityEvents.selectByExample(filter);
            if (facts.size() != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            var fact = facts.getFirst();
            var execution = productionExecution(fact.getExecutionId());
            if (!Action.TRANSFER_WAREHOUSE.getValue().equals(fact.getActionType())
                    || !Objects.equals(execution.getProductId(), line.getMaterialId())
                    || !Objects.equals(fact.getTargetWarehouseId(), line.getWarehouseId())
                    || fact.getTransferQty().compareTo(line.getQuantity()) != 0)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            return fact.getTransferQty();
        }
        return null;
    }

    private void validateIdentity(WmStockDocumentLineEntity line, BigDecimal remaining, boolean checkQuantity) {
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
        if (checkQuantity) quantityRules.checkSettlementForWrite(line.getQuantity(), remaining, material.getId());
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
        try {
            if (documents.updateByPrimaryKeyWithBLOBs(row) != 1)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, conflict);
        }
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

    private static void requireDirection(String direction, StockDocumentType purpose) {
        var flow = StockDocumentDirection.from(direction);
        if (flow == null || flow != purpose.getDirection()) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static String numberingCategory(String direction) {
        if (StockDocumentDirection.IN.getValue().equals(direction)) return AutoCodeRuleType.STOCK_IN.name();
        if (StockDocumentDirection.OUT.getValue().equals(direction)) return AutoCodeRuleType.STOCK_OUT.name();
        ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return null;
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
