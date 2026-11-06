package dev.mesbase.business.wm.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesProductionReceiptExample;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Allocation;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.domain.MesQualityTreatmentExample;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.wm.domain.StockDocumentModels.CancellationBasis;
import dev.mesbase.business.wm.domain.StockDocumentModels.Detail;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.utils.DictDataLookup;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.system.mapper.SysDictDataMapper;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * LOCAL document lifecycle; all stock mutations remain in LocalStockService and its caller's transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class StockDocumentService {
    private static final Logger LOG = LoggerFactory.getLogger(StockDocumentService.class);
    private final dev.mesbase.business.purchase.service.impl.PurchaseReceiptPricing purchasing;
    private final WmStockDocumentMapper documents;
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
    private final MesWorkorderMaterialMapper workorderMaterials;
    private final MesQualityTreatmentMapper qualityEvents;
    private final AutoCodeNumbering autoCodes;
    private final DictDataLookup documentTypeDictionary;
    private final PlatformPersistence center;
    private final TransactionTemplate businessTransactions;

    public StockDocumentService(WmStockDocumentMapper documents, WmStockDocumentLineMapper lines,
                                WmInventoryMapper inventories, MaterialMapper materials, UnitMeasureMapper units,
                                LocalStockService stock, BusinessSourceRules sources, ProductionChainLocks locks,
                                MesProductionExecutionMapper executions, MesProductionReceiptMapper receipts,
                                Validator validator, StockDocumentExtraMapper inbound,
                                ProductionQuantityService quantities, QualityTreatmentService quality,
                                WorkorderMapper orders, MesWorkorderMaterialMapper workorderMaterials,
                                MesQualityTreatmentMapper qualityEvents,
                                ObjectProvider<MaterialIssueInputFacts> inputFacts,
                                ExecutionPathService executionPaths, MaterialQuantityRules quantityRules,
                                AutoCodeNumbering autoCodes,
                                DictDataLookup documentTypeDictionary,
                                PlatformPersistence center,
                                @Qualifier("businessTransactionManager") PlatformTransactionManager businessManager,
                                dev.mesbase.business.purchase.service.impl.PurchaseReceiptPricing purchasing) {
        this.purchasing = purchasing;
        this.autoCodes = autoCodes;
        this.documentTypeDictionary = documentTypeDictionary;
        this.center = center;
        this.businessTransactions = new TransactionTemplate(businessManager);
        this.businessTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.inputFacts = inputFacts;
        this.executionPaths = executionPaths;
        this.qualityEvents = qualityEvents;
        this.orders = orders;
        this.workorderMaterials = workorderMaterials;
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
        if (StockDocumentDirection.from(q.direction()) == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("收发方向无效，请选择入库或出库"));
        int offset = q.offset() == null ? 0 : q.offset();
        int limit = q.limit() == null ? 20 : q.limit();
        if (offset < 0 || limit < 1 || limit > 100)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("分页参数无效，请检查起始位置和每页条数"));
        var f = new WmStockDocumentExample();
        var c = f.createCriteria();
        if (StringUtils.isNotBlank(q.documentType())) {
            var selectedType = type(q.documentType());
            if (!selectedType.getDirection().getValue().equals(q.direction()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("所选单据类型与收发方向不匹配"));
            c.andDocumentTypeEqualTo(q.documentType());
        }
        c.andDirectionEqualTo(q.direction());
        if (StringUtils.isNotBlank(q.status())) {
            if (!List.of(StockDocumentStatus.DRAFT.getValue(), StockDocumentStatus.POSTED.getValue(), StockDocumentStatus.CANCELLED.getValue())
                    .contains(q.status()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据状态筛选值无效，请重新选择"));
            c.andStatusEqualTo(q.status());
        }
        if (StringUtils.isNotBlank(q.documentNo())) {
            if (q.documentNo().length() > 64)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据编号筛选不能超过64个字符"));
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
        var purpose = StockDocumentType.from(row.getDocumentType());
        StockDocumentModels.QualitySource qualitySource = null;
        if (purpose != null && purpose.isQualityReceipt() && row.getPostRequestId() != null) {
            var event = qualityEvents.selectByPrimaryKey(row.getPostRequestId());
            if (event != null && Objects.equals(id, event.getStockDocumentId()))
                qualitySource = new StockDocumentModels.QualitySource(event.getDeclarationId(), event.getId(), event.getExecutionCode());
        }
        StockDocumentModels.MaterialIssueBasis materialIssueBasis = null;
        String materialIssueBasisError = null;
        if (purpose == StockDocumentType.MATERIAL_ISSUE && row.getSourceRequestJson() != null) {
            try {
                var request = new ObjectMapper().readValue(row.getSourceRequestJson(), ProductionInputService.Command.class);
                if (request == null || request.requestId() == null || request.code() == null
                        || request.workorderId() == null || request.outputQty() == null
                        || request.routingNodeIds() == null
                        || request.routingId() != null && request.routingNodeIds().isEmpty()
                        || request.routingId() == null && !request.routingNodeIds().isEmpty()) {
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
        var input = inputFacts.getObject().find(documentId);
        if (input == null) return null;
        var combination = inputFacts.getObject().combinationSnapshot(documentId);
        Calculation calculation = null;
        if (combination == null) calculation = inputFacts.getObject().snapshot(documentId).calculation();
        List<Long> selectedNodes = List.of();
        Integer pathRevision = null;
        String pathError = null;
        if (document.getSourceExecutionCode() != null) {
            try {
                var path = executionPaths.view(document.getSourceExecutionCode());
                selectedNodes = path.selectedNodeIds();
                if (path.path() != null) pathRevision = path.path().getRevision();
            } catch (FoundationException failure) {
                if (failure.code() != ErrorCode.RESOURCE_CONFLICT
                        && failure.code() != ErrorCode.EXECUTION_NOT_FOUND) throw failure;
                pathError = "实际路径读取失败；领料投入与库存明细仍可查看";
            }
        }
        Long bomId = null;
        BigDecimal bomBase = null;
        BigDecimal bomComponent = null;
        var materialOrder = orders.selectByPrimaryKey(document.getSourceWorkorderId());
        if (materialOrder != null) bomId = materialOrder.getBomId();
        var materialFilter = new MesWorkorderMaterialExample();
        materialFilter.createCriteria().andWorkorderIdEqualTo(document.getSourceWorkorderId());
        var materialRows = workorderMaterials.selectByExample(materialFilter);
        if (materialRows.size() == 1) {
            var material = materialRows.getFirst();
            bomBase = material.getBomBaseQty();
            bomComponent = material.getBomComponentQty();
        }
        String voidOperationId = null;
        String voidResultStatus = null;
        var reversal = inputFacts.getObject().recordedReversal(documentId);
        if (reversal != null) {
            voidOperationId = reversal.getId();
            voidResultStatus = "VOIDED";
        }
        if (combination != null) {
            var order = orders.selectByPrimaryKey(combination.workorderId());
            String code = null;
            if (order != null) code = order.getCode();
            return new ProductionInputBasis(input.getId(), input.getInputKind(), input.getSourceMode(),
                    input.getExecutionCode(), combination.workorderId(), code, input.getOutputQty(), null,
                    null, null, null, null, null, bomId, null, null, pathRevision, selectedNodes,
                    input.getBusinessOccurredAt(), input.getConfirmedAt(), input.getStockApplied(),
                    voidOperationId, voidResultStatus, pathError, combination.combinationId(), combination.allocations());
        }
        return new ProductionInputBasis(input.getId(), input.getInputKind(), input.getSourceMode(),
                input.getExecutionCode(), calculation.order().workorderId(), calculation.order().workorderCode(),
                input.getOutputQty(), calculation.issueQty(), calculation.stock().materialId(),
                calculation.stock().materialCode(), calculation.stock().warehouseId(),
                calculation.stock().unitId(), calculation.stock().lotCode(),
                bomId, bomBase, bomComponent, pathRevision, selectedNodes,
                input.getBusinessOccurredAt(), input.getConfirmedAt(), input.getStockApplied(),
                voidOperationId, voidResultStatus, pathError, null, List.of());
    }

    public record ProductionInputBasis(String requestId, String inputKind, String sourceMode,
                                       String executionCode, Long workorderId, String workorderCode,
                                       BigDecimal outputQty, BigDecimal issueQty, Long materialId,
                                       String materialCode, Long warehouseId, Long unitId, String batchNo,
                                       Long bomId, BigDecimal bomBaseQty, BigDecimal bomComponentQty,
                                       Integer pathRevision, List<Long> routingNodeIds,
                                       LocalDateTime businessOccurredAt,
                                       LocalDateTime confirmedAt, Boolean stockApplied,
                                       String voidOperationId, String voidResultStatus, String pathError,
                                       Long combinationId, List<Allocation> allocations) {
    }

    public record DocumentTypeOption(String value, String direction, String label, String origin,
                                     String requiredDocuments, boolean approvalAvailable,
                                     boolean qualityReceipt, boolean enabled, int sort) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<DocumentTypeOption> typeOptions(String direction) {
        StockDocumentDirection flow = StockDocumentDirection.from(direction);
        if (flow == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单方向无效，请选择入库或出库"));
        String dictionary = flow == StockDocumentDirection.IN ? "stock_document_in_type" : "stock_document_out_type";
        List<SysDictData> selectedRows = dictionaryRows(dictionary);
        List<SysDictData> rows = selectedRows == null ? List.of() : selectedRows;
        return Arrays.stream(StockDocumentType.values()).filter(type -> type.getDirection() == flow)
                .map(type -> {
                    SysDictData row = rows.stream().filter(value -> type.getValue().equals(value.getDictValue()))
                            .findFirst().orElse(null);
                    return new DocumentTypeOption(type.getValue(), direction,
                            row == null ? type.getDescription() : row.getDictLabel(), type.getOrigin(),
                            type.getRequiredDocuments(), type.isApprovalAvailable(), type.isQualityReceipt(),
                            row != null && "0".equals(row.getStatus()), row == null || row.getDictSort() == null
                            ? Integer.MAX_VALUE : row.getDictSort().intValue());
                }).sorted(Comparator.comparingInt(DocumentTypeOption::sort)).toList();
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            readOnly = true)
    public void requireTypeActive(String direction, String typeKey) {
        StockDocumentType purpose = type(typeKey);
        requireDirection(direction, purpose);
        String dictionary = StockDocumentDirection.IN.getValue().equals(direction)
                ? "stock_document_in_type" : "stock_document_out_type";
        List<SysDictData> rows = dictionaryRows(dictionary);
        if (rows == null || rows.stream().noneMatch(row -> typeKey.equals(row.getDictValue())
                && "0".equals(row.getStatus()))) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
    }

    private List<SysDictData> dictionaryRows(String dictionary) {
        if (center.transactionManager().foreignTransactionActive())
            return center.readOnly(session -> session.mapper(SysDictDataMapper.class).selectDictDataByType(dictionary));
        return documentTypeDictionary.selectDictDataByType(dictionary);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public Detail create(StockDocumentModels.Create command) {
        validate(command);
        StockDocumentType purpose = type(command.documentType());
        if (purpose == StockDocumentType.MATERIAL_ISSUE && command.sourceExecutionCode() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("普通领料单不能关联生产执行，请使用生产领料入口"));
        if (purpose.isTransfer() || purpose.isQualityReceipt())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        requireDirection(command.direction(), purpose);
        requireTypeActive(command.direction(), command.documentType());
        String category = numberingCategory(command.direction());
        String ruleCode = command.ruleCode() == null || command.ruleCode().isBlank()
                ? purpose.getValue() : command.ruleCode();
        if (!purpose.getValue().equals(ruleCode))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据编号规则必须与单据类型一致"));
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
        requireTypeActive(header.direction(), header.documentType());
        if (command.productionIssue() != null || purpose == StockDocumentType.MATERIAL_ISSUE && header.sourceExecutionCode() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("普通库存单不能携带生产领料数据或关联生产执行"));
        if (purpose.isTransfer() || purpose.isQualityReceipt())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        String category = numberingCategory(header.direction());
        if (header.ruleCode() == null || !header.documentType().equals(header.ruleCode()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据编号规则必须与单据类型一致"));
        String documentNo = autoCodes.getBusinessDocNo(header.ruleCode(), category);
        return businessTransactions.execute(status -> {
            var draft = createDraft(new StockDocumentModels.Create(header.direction(), header.documentType(), header.reason(),
                    header.remark(), header.sourceWorkorderId(), header.sourceExecutionCode(), header.ruleCode(), header.supplierName(), header.supplierDocumentNo(), header.supplierId(), header.businessDate(), header.currency()), documentNo);
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
        if (command.productionIssue() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("普通库存单不能携带生产领料数据"));
        if (current.getSourceRequestJson() != null && !type(current.getDocumentType()).isTransfer()
                || !Objects.equals(current.getSourceWorkorderId(), header.sourceWorkorderId())
                || !Objects.equals(current.getSourceExecutionCode(), header.sourceExecutionCode())
                || !Objects.equals(current.getDocumentType(), header.documentType())
                || !Objects.equals(current.getDirection(), header.direction()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        edit(id, new StockDocumentModels.Edit(header.expectedRevision(), header.reason(), header.remark(), header.supplierName(), header.supplierDocumentNo(), header.supplierId(), header.businessDate(), header.currency()));
        var existing = rows(id);
        var retained = new HashSet<Long>();
        for (StockDocumentModels.AggregateLine item : command.items()) {
            if (item.id() != null && !retained.add(item.id()))
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
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
                item.unitId(), item.batchNo(), item.binCode(), item.quantity(), item.remark(), item.unitPrice());
    }

    private void validateAggregate(StockDocumentModels.AggregateSave command, boolean creating) {
        if (command == null || command.header() == null || command.items() == null || command.items().isEmpty()
                || command.items().size() > 100)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据必须包含主单信息及1至100条明细"));
        if (creating && command.header().expectedRevision() != null
                || !creating && command.header().expectedRevision() == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据版本信息与新建或编辑操作不匹配，请刷新后重试"));
        validate(new StockDocumentModels.Create(command.header().direction(), command.header()
                .documentType(), command.header().reason(),
                command.header().remark(), command.header().sourceWorkorderId(), command.header().sourceExecutionCode(),
                command.header().ruleCode(), command.header().supplierName(), command.header()
                .supplierDocumentNo(), command.header().supplierId(), command.header().businessDate(), command.header()
                .currency()));
        requireDirection(command.header().direction(), type(command.header().documentType()));
        for (StockDocumentModels.AggregateLine item : command.items()) {
            if (item == null || item.stockId() == null || item.materialId() == null || item.warehouseId() == null
                    || item.unitId() == null || item.quantity() == null || item.quantity().signum() <= 0
                    || item.id() != null && creating)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                        new SafeBusinessMessage("每条单据明细必须指定库存、物料、仓库、单位及大于零的数量；新建时不能携带已有明细ID"));
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Detail createQualityReceipt(StockDocumentModels.Create command, String documentNo) {
        validate(command);
        if (!type(command.documentType()).isQualityReceipt() || command.sourceExecutionCode() != null || command.sourceWorkorderId() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("质量入库必须通过质量处置流程创建"));
        if (!command.documentType().equals(command.ruleCode()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据类型与编号规则不匹配"));
        requireTypeActive(command.direction(), command.documentType());
        return createDraft(command, documentNo);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public Detail createWithNumber(StockDocumentModels.Create command, String documentNo) {
        validate(command);
        StockDocumentType purpose = type(command.documentType());
        requireDirection(command.direction(), purpose);
        if (!command.documentType().equals(command.ruleCode()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据类型与编号规则不匹配"));
        requireTypeActive(command.direction(), command.documentType());
        if (purpose.isQualityReceipt())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        return createDraft(command, documentNo);
    }

    private Detail createDraft(StockDocumentModels.Create command, String documentNo) {
        var purpose = type(command.documentType());
        requireDirection(command.direction(), purpose);
        if (purpose == StockDocumentType.PRODUCTION_RECEIPT && command.sourceExecutionCode() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        sources.localCreation();
        if (command.sourceExecutionCode() != null) {
            if (purpose == StockDocumentType.PRODUCTION_RECEIPT) {
                if (command.sourceWorkorderId() != null)
                    ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("生产入库不能同时指定工单来源"));
                productionExecution(command.sourceExecutionCode());
            } else if (purpose != StockDocumentType.MATERIAL_ISSUE)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("当前单据类型不能关联生产执行"));
        }
        if (command.sourceWorkorderId() != null) {
            if (purpose == StockDocumentType.MATERIAL_ISSUE) sources.order(command.sourceWorkorderId());
            else {
                if (purpose != StockDocumentType.OTHER_RECEIPT)
                    ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("当前单据类型不能关联工单来源"));
                cancelled(command.sourceWorkorderId());
            }
        }
        var row = new WmStockDocumentEntity();
        if (documentNo == null || documentNo.isBlank())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单编号不能为空"));
        row.setDocumentNo(documentNo);
        row.setDocumentType(command.documentType());
        row.setDirection(command.direction());
        row.setStatus(StockDocumentStatus.DRAFT.getValue());
        row.setReason(command.reason());
        row.setRemark(command.remark());
        purchaseSupplier(row, command.supplierName(), command.supplierDocumentNo(), command.supplierId(), command.businessDate(), command.currency());
        row.setSourceWorkorderId(command.sourceWorkorderId());
        row.setSourceExecutionCode(command.sourceExecutionCode());
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
        purchaseSupplier(row, command.supplierName(), command.supplierDocumentNo(), command.supplierId(), command.businessDate(), command.currency());
        if (type(row.getDocumentType()) == StockDocumentType.PURCHASE_RECEIPT) {
            // Existing document limit is 100; same transaction, first failure aborts the entire edit.
            for (var line : rows(id)) {
                purchasing.line(row, line, line.getUnitPrice());
                line.setRevision(line.getRevision() + 1);
                if (lines.updateByPrimaryKey(line) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
            }
        }
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
        if (header.getSourceRequestJson() != null && !type(header.getDocumentType()).isTransfer())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var existing = rows(documentId);
        if (lineId == null && existing.size() >= 100)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据明细不能超过100行"));
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
        if (type(header.getDocumentType()) == StockDocumentType.PURCHASE_RECEIPT)
            purchasing.line(header, line, command.unitPrice());
        else if (command.unitPrice() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("成交价格仅适用于采购入库"));
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
        if (row.getSourceRequestJson() != null && !type(row.getDocumentType()).isTransfer())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        requireLine(documentId, lineId);
        if (lines.deleteByPrimaryKey(lineId) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        save(row);
    }

    /**
     * Lock the unified issue header after its work order and execution locks have been acquired.
     */
    public WmStockDocumentEntity lockMaterialIssueForVoid(Long documentId) {
        var row = lock(documentId);
        if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(row.getDocumentType()))
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return row;
    }

    public Detail approve(Long id, StockDocumentModels.Post command) {
        var before = require(id);
        if (StockDocumentType.PRODUCTION_RECEIPT.getValue().equals(before.getDocumentType())
                && before.getSourceExecutionCode() != null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        return approve(id, command, false);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Detail approveMesReceipt(Long id, StockDocumentModels.Post command) {
        var row = require(id);
        if (!StockDocumentType.PRODUCTION_RECEIPT.getValue()
                .equals(row.getDocumentType()) || row.getSourceExecutionCode() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        return approve(id, command, true);
    }

    /**
     * Called only by a MES command already owning its production transaction and authorization.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Detail createMesIssue(Long workorderId, String executionCode, String documentNo, String requestId,
                                 String frozenRequest, List<Allocation> allocations) {
        var draft = createDraft(new StockDocumentModels.Create(StockDocumentDirection.OUT.getValue(),
                StockDocumentType.MATERIAL_ISSUE.getValue(), "MES扫码生产领料", null, workorderId, executionCode,
                StockDocumentType.MATERIAL_ISSUE.getValue()), documentNo);
        for (var allocation : allocations) {
            var current = require(draft.document().getId());
            var line = putLine(current.getId(), null, new StockDocumentModels.Line(current.getRevision(), allocation.stockId(),
                    allocation.materialId(), allocation.warehouseId(), allocation.unitId(), allocation.batchNo(),
                    allocation.binCode(), allocation.quantity(), null));
            line.setDemandLineId(allocation.demandLineId());
            if (lines.updateByPrimaryKey(line) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        }
        var row = lock(draft.document().getId());
        row.setCreateRequestId(requestId);
        row.setSourceRequestJson(frozenRequest);
        if (documents.updateByPrimaryKeyWithBLOBs(row) != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return approve(row.getId(), new StockDocumentModels.Post(requestId, row.getRevision()), true);
    }

    private Detail approve(Long id, StockDocumentModels.Post command, boolean productionCommand) {
        validate(command);
        uuid(command.requestId());
        String hash = RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(
                List.of(id, command.requestId(), command.expectedRevision(), SecurityUtils.getUserId())));
        var before = require(id);
        var purposeBefore = type(before.getDocumentType());
        if (purposeBefore.isTransfer()) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        if (purposeBefore == StockDocumentType.MATERIAL_ISSUE && !productionCommand
                && before.getSourceRequestJson() != null && StockDocumentStatus.DRAFT.getValue()
                .equals(before.getStatus()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        // Original frozen replay is a read: a later factory mode cannot rewrite already committed LOCAL facts.
        if (replay(before, command, hash)) return detail(id);
        if (before.getSourceWorkorderId() != null && purposeBefore != StockDocumentType.MATERIAL_ISSUE)
            cancelled(before.getSourceWorkorderId());
        String executionCode = before.getSourceExecutionCode();
        if (type(before.getDocumentType()).isQualityReceipt()) {
            var event = qualityEvents.selectByPrimaryKey(command.requestId());
            if (event == null || !Objects.equals(id, event.getStockDocumentId()))
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            executionCode = event.getExecutionCode();
        }
        var row = lock(id);
        if (replay(row, command, hash)) return detail(id);
        draft(row, command.expectedRevision());
        sources.localCreation();
        var purpose = type(row.getDocumentType());
        if (!purpose.isApprovalAvailable()) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        if (purpose == StockDocumentType.PURCHASE_RECEIPT) {
            // V1 direct receipt: this document is the receiving basis; a purchase order is optional in future.
            if (StringUtils.isBlank(row.getSupplierName()) || row.getSourceWorkorderId() != null
                    || row.getSourceExecutionCode() != null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("采购入库请填写供应商，不得关联生产工单或执行"));
        }
        var detail = rows(id);
        if (detail.isEmpty()) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        if (purpose == StockDocumentType.PURCHASE_RECEIPT) purchasing.validatePosted(row, detail);
        if (purpose.isQualityReceipt()) {
            var fact = qualityEvents.selectByPrimaryKey(command.requestId());
            if (fact == null || !Action.TRANSFER_WAREHOUSE.getValue().equals(fact.getActionType())
                    || !Objects.equals(id, fact.getStockDocumentId()) || detail.size() != 1
                    || !Objects.equals(detail.getFirst().getWarehouseId(), fact.getTargetWarehouseId())
                    || detail.getFirst().getQuantity().compareTo(fact.getTransferQty()) != 0)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        }
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
        if (row.getSourceExecutionCode() != null && purpose != StockDocumentType.MATERIAL_ISSUE) {
            var basis = productionBasis(row.getSourceExecutionCode());
            var execution = productionExecution(row.getSourceExecutionCode());
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
                var action = stock.postDocument(row.getDirection(), row.getDocumentType(), id, line, command.requestId(), actionId,
                        hash, row.getReason(), executionCode);
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
        return detail(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public String findDocumentIdForRequest(String requestId) {
        uuid(requestId);
        var input = inputFacts.getObject().findByRequestId(requestId);
        if (input != null) {
            var document = require(input.getDocumentId());
            if (type(document.getDocumentType()) != StockDocumentType.MATERIAL_ISSUE)
                throw new FoundationException(ErrorCode.NOT_FOUND);
            return document.getId().toString();
        }
        var filter = new WmStockDocumentExample();
        filter.createCriteria().andCreateRequestIdEqualTo(requestId)
                .andDocumentTypeEqualTo(StockDocumentType.MATERIAL_ISSUE.getValue());
        var found = documents.selectByExample(filter);
        if (found.size() > 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        if (found.isEmpty()) return null;
        return found.getFirst().getId().toString();
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public StockDocumentModels.ProductionBasis productionBasis(String executionCode) {
        var execution = executions.selectByCode(executionCode);
        if (execution == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var order = orders.selectByPrimaryKey(execution.getWorkorderId());
        boolean admitted = order != null && List.of(WorkOrderStatus.RELEASED.getValue(), WorkOrderStatus.COMPLETED.getValue())
                .contains(order.getStatus())
                && BusinessSource.LOCAL.getValue().equals(order.getSource()) && FactoryIntegrationContext.required()
                .mode() == FactoryRuntimeMode.LOCAL;
        var projection = quantities.getExecutionQuantities(executionCode);
        var basis = quality.productionBasis(executionCode);
        var effective = projection.nodes().isEmpty() ? BigDecimal.ZERO : projection.nodes().getLast()
                .effectiveOutputQty();
        boolean reportingRequired = execution.getRoutingId() != null;
        if (!reportingRequired) effective = projection.confirmedInputQty();
        var received = inbound.netProductionInbound(executionCode);
        var remaining = effective.subtract(received);
        String blocked = null;
        if (!admitted) blocked = "当前工单状态、来源或模式不允许入库";
        else if (!ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())
                && (reportingRequired || !ProductionExecutionStatus.READY.getValue().equals(execution.getStatus())))
            blocked = "执行状态不允许入库";
        else if (reportingRequired && (!ExecutionPathStatus.LOCKED.getValue()
                .equals(projection.pathStatus()) || projection.nodes().isEmpty()))
            blocked = "实际生产路径尚未锁定";
        else if (projection.confirmedInputQty().signum() <= 0 || projection.reservedInputQty().signum() > 0)
            blocked = "缺少正式投入或仍有未决投产意图";
        else if (projection.nodes().stream().anyMatch(node -> node.unverifiedDefectQty().signum() > 0)
                || basis.declarations().stream()
                .anyMatch(declaration -> declaration.attribution() == Attribution.UNVERIFIED))
            blocked = "存在待核实不良，不能审核生产入库";
        else if (remaining.signum() <= 0) blocked = "可入库数量不足或存在数量超额";
        return new StockDocumentModels.ProductionBasis(executionCode, execution.getProductId(), effective,
                received, remaining.max(BigDecimal.ZERO), blocked == null, blocked, reportingRequired);
    }

    private MesProductionExecutionEntity productionExecution(String id) {
        var execution = locks.execution(id);
        if (!ProductionExecutionStatus.RUNNING.getValue().equals(execution.getStatus())
                && (execution.getRoutingId() != null || !ProductionExecutionStatus.READY.getValue()
                .equals(execution.getStatus())))
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
        for (var execution : executionRows) locks.execution(execution.getExecutionCode());
        BigDecimal handed = BigDecimal.ZERO;
        BigDecimal accepted = inbound.netProductionInboundForOrder(workorderId);
        if (!executionRows.isEmpty()) {
            var ids = executionRows.stream().map(row -> row.getExecutionCode()).toList();
            var r = new MesProductionReceiptExample();
            r.createCriteria().andExecutionCodeIn(ids);
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
        if (type(header.getDocumentType()) == StockDocumentType.PRODUCTION_RECEIPT && header.getSourceExecutionCode() != null) {
            var basis = productionBasis(header.getSourceExecutionCode());
            if (Objects.equals(basis.productId(), line.getMaterialId())) return basis.remaining();
        }
        if (type(header.getDocumentType()).isQualityReceipt()) {
            var filter = new MesQualityTreatmentExample();
            filter.createCriteria().andStockDocumentIdEqualTo(header.getId());
            var facts = qualityEvents.selectByExample(filter);
            if (facts.size() != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            var fact = facts.getFirst();
            var execution = productionExecution(fact.getExecutionCode());
            if (!Action.TRANSFER_WAREHOUSE.getValue().equals(fact.getActionType())
                    || !Objects.equals(execution.getProductId(), line.getMaterialId())
                    || !Objects.equals(fact.getTargetWarehouseId(), line.getWarehouseId())
                    || fact.getTransferQty().compareTo(line.getQuantity()) != 0)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            return fact.getTransferQty();
        }
        return null;
    }

    private void purchaseSupplier(WmStockDocumentEntity row, String name, String documentNo, Long supplierId,
                                  java.time.LocalDate date, String currency) {
        if (type(row.getDocumentType()) != StockDocumentType.PURCHASE_RECEIPT) {
            if (StringUtils.isNotBlank(name) || StringUtils.isNotBlank(documentNo) || supplierId != null || date != null || currency != null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("供应商信息仅适用于采购入库"));
            return;
        }
        purchasing.header(row, supplierId, date, currency);
        row.setSupplierDocumentNo(StringUtils.trimToNull(documentNo));
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
        if (id == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单ID不能为空"));
        var row = documents.selectByPrimaryKeyForUpdate(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private WmStockDocumentEntity require(Long id) {
        if (id == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单ID不能为空"));
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
        if (command == null || !validator.validate(command).isEmpty())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单数据校验失败，请检查必填项和字段格式"));
    }

    private static void requireDirection(String direction, StockDocumentType purpose) {
        var flow = StockDocumentDirection.from(direction);
        if (flow == null || flow != purpose.getDirection())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("单据方向与单据类型不匹配"));
    }

    private static String numberingCategory(String direction) {
        if (StockDocumentDirection.IN.getValue().equals(direction)) return AutoCodeRuleType.STOCK_IN.name();
        if (StockDocumentDirection.OUT.getValue().equals(direction)) return AutoCodeRuleType.STOCK_OUT.name();
        ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单方向无效，请选择入库或出库"));
        return null;
    }

    private static void draft(WmStockDocumentEntity row, Long revision) {
        if (!StockDocumentStatus.DRAFT.getValue()
                .equals(row.getStatus()) || !Objects.equals(row.getRevision(), revision))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
    }

    private static StockDocumentType type(String value) {
        var type = StockDocumentType.from(value);
        if (type == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单类型无效"));
        return type;
    }

    private static void uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请求标识格式无效，请使用标准UUID"));
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure, new SafeBusinessMessage("请求标识格式无效，请使用标准UUID"));
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
