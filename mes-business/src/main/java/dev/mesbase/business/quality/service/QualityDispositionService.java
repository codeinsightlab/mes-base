package dev.mesbase.business.quality.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Destination;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Disposition;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WarehouseType;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmInventoryCreateCommand;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.service.IWmInventoryService;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic formal warehouse receipt; historical return requests remain replayable.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityDispositionService {
    private final QualityTreatmentService treatments;
    private final ProductionChainLocks locks;
    private final BusinessSourceRules sources;
    private final ObjectProvider<ProductionCompletionService> completion;
    private final ObjectProvider<StockDocumentService> documents;
    private final ObjectProvider<IWmInventoryService> inventory;
    private final WmInventoryMapper stocks;
    private final StockReferenceRules references;
    private final MaterialMapper materials;
    private final AuthorizationService authorization;
    private final ProductionIdentityService identities;
    private final WmWarehouseMapper warehouses;

    public QualityDispositionService(QualityTreatmentService treatments, ProductionChainLocks locks,
                                     BusinessSourceRules sources,
                                     ObjectProvider<ProductionCompletionService> completion,
                                     ObjectProvider<StockDocumentService> documents,
                                     ObjectProvider<IWmInventoryService> inventory, WmInventoryMapper stocks,
                                     StockReferenceRules references,
                                     MaterialMapper materials, AuthorizationService authorization,
                                     ProductionIdentityService identities, WmWarehouseMapper warehouses) {
        this.warehouses = warehouses;
        this.identities = identities;
        this.treatments = treatments;
        this.locks = locks;
        this.sources = sources;
        this.completion = completion;
        this.documents = documents;
        this.inventory = inventory;
        this.stocks = stocks;
        this.references = references;
        this.materials = materials;
        this.authorization = authorization;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public QualityTreatmentService.Details execute(String declaration, Disposition c) {
        if (c == null || c.destination() == null || c.requestId() == null || c.quantity() == null
                || c.quantity().signum() <= 0 || StringUtils.isBlank(c.reason()) || c.reason().length() > 500
                || c.destination() == Destination.RETURN_NODE && c.warehouseId() != null
                || c.destination() == Destination.WAREHOUSE && c.warehouseId() == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        try {
            if (!UUID.fromString(c.requestId()).toString().equals(c.requestId()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException failure) {
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
        ProductionQuantityInput.check(c.quantity());
        String hash = hash(declaration, c);
        if (replayed(declaration, c.requestId(), hash)) return treatments.details(declaration);
        if (c.destination() == Destination.RETURN_NODE)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        var d = treatments.latest(declaration);
        completion.getObject().lockInboundChain(d.getExecutionId());
        var execution = locks.execution(d.getExecutionId());
        if (replayed(declaration, c.requestId(), hash)) return treatments.details(declaration);
        locks.requireActiveExecution(execution.getId());
        if (!ProductionExecutionStatus.allowsNormalReporting(execution.getStatus()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        sources.execution(execution, c.warehouseId());
        var s = treatments.summary(declaration);
        if (DefectRecordAction.VOID.getValue()
                .equals(d.getRecordAction())) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        if (c.expectedRevision() != s.revision() || s.attribution() != Attribution.INDEPENDENT
                || c.quantity().compareTo(s.pendingDispositionQty()) > 0)
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        Long documentId = null;
        if (c.destination() == Destination.WAREHOUSE) {
            permit("business:wm:stock:document:create");
            permit("business:wm:stock:document:post");
            if (BusinessSourceRules.required(sources.order(execution.getWorkorderId())
                    .getSource()) != BusinessSource.LOCAL)
                throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            var batchNo = identities.requiredBatch(execution.getId());
            references.lockDemandReferences(execution.getProductId(), List.of(c.warehouseId()));
            var warehouse = warehouses.selectByPrimaryKey(c.warehouseId());
            var warehouseType = WarehouseType.from(warehouse.getWarehouseType());
            if (warehouseType != WarehouseType.DEFECTIVE && warehouseType != WarehouseType.SCRAP)
                ParameterChecks.reject(ErrorCode.RECEIPT_WAREHOUSE_INVALID);
            var receiptType = warehouseType == WarehouseType.DEFECTIVE ? StockDocumentType.DEFECT_RECEIPT : StockDocumentType.SCRAP_RECEIPT;
            var material = materials.selectByPrimaryKey(execution.getProductId());
            if (material == null || material.getUnitId() == null)
                ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            var f = new WmInventoryExample();
            f.createCriteria().andMaterialIdEqualTo(execution.getProductId()).andWarehouseIdEqualTo(c.warehouseId())
                    .andBatchNoEqualTo(batchNo).andBinCodeIsNull();
            var rows = stocks.selectByExample(f);
            if (rows.size() > 1) ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            Long stockId = rows.isEmpty() ? inventory.getObject()
                    .insert(new WmInventoryCreateCommand(execution.getProductId(),
                            c.warehouseId(), BigDecimal.ZERO, BigDecimal.ZERO, EnableStatus.ENABLED.getValue(), "质量转仓库存来源", batchNo, null))
                    .inventory().getId() : rows.getFirst().getId();
            var owner = documents.getObject();
            var doc = owner.createQualityReceipt(new StockDocumentModels.Create(receiptType.getValue(), c.reason()
                    .trim(), "质量申报 " + declaration, null, null));
            documentId = doc.document().getId();
            owner.putLine(documentId, null, new StockDocumentModels.Line(doc.document().getRevision(), stockId,
                    execution.getProductId(), c.warehouseId(), material.getUnitId(), batchNo, null,
                    c.quantity(), "质量处置 " + c.requestId()));
            treatments.recordDisposition(declaration, c, hash, documentId);
            owner.approve(documentId, new StockDocumentModels.Post(c.requestId(), owner.detail(documentId).document()
                    .getRevision()));
        }
        return treatments.details(declaration);
    }

    private boolean replayed(String declaration, String id, String hash) {
        MesQualityTreatmentEntity event = treatments.dispositionEvent(id);
        if (event == null) return false;
        if (!Objects.equals(declaration, event.getDeclarationId()) || !Objects.equals(hash, event.getRequestHash()))
            ParameterChecks.reject(ErrorCode.QUALITY_TREATMENT_CONFLICT);
        return true;
    }

    private void permit(String permission) {
        if (!authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                FactoryContext.required(), new Permission(permission)))
            throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    private static String hash(String declaration, Disposition c) {
        try {
            String value = String.join("\n", "DISPOSITION", declaration, SecurityUtils.getUserId().toString(),
                    Integer.toString(c.expectedRevision()), c.destination().name(), c.quantity().stripTrailingZeros()
                            .toPlainString(),
                    Objects.toString(c.warehouseId(), ""), c.reason().trim());
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }


}
