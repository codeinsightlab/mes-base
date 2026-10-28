package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Production-input facts stored on the unified stock-document header. */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueInputFacts {
    private final WmStockDocumentMapper documents;
    private final LocalStockService inventory;
    private final ObjectProvider<StockDocumentService> stockDocuments;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueInputFacts(WmStockDocumentMapper documents, LocalStockService inventory,
                                   ObjectProvider<StockDocumentService> stockDocuments) {
        this.documents = documents;
        this.inventory = inventory;
        this.stockDocuments = stockDocuments;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void freeze(WmStockDocumentEntity document, Calculation calculation) {
        if (document.getProductionRequestId() == null || document.getProductionRequestHash() == null
                || document.getSourceExecutionId() == null
                || !Mode.LOCAL_ONLY.getValue().equals(document.getProductionSourceMode())
                || !Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode()))
            ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        inventory.validateInputSource(document, calculation);
        if (documents.updateByPrimaryKeyWithBLOBs(document) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public WmStockDocumentEntity find(Long documentId) {
        var document = documents.selectByPrimaryKey(documentId);
        if (document == null || !StockDocumentType.MATERIAL_ISSUE.getValue().equals(document.getDocumentType())
                || document.getProductionRequestId() == null) return null;
        return document;
    }

    public WmStockDocumentEntity findByRequestId(String requestId) {
        var filter = new WmStockDocumentExample();
        filter.createCriteria().andProductionRequestIdEqualTo(requestId).andSourceExecutionIdIsNotNull();
        var rows = documents.selectByExampleWithBLOBs(filter);
        if (rows.size() > 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public record LocalInputSnapshot(String requestId, String inputKind, BigDecimal outputQty,
                                     Calculation calculation) { }

    public LocalInputSnapshot snapshot(Long documentId) {
        var document = find(documentId);
        if (document == null) return null;
        return new LocalInputSnapshot(document.getProductionRequestId(), document.getProductionInputKind(),
                document.getProductionOutputQty(),
                json.readValue(document.getProductionCalculationJson(), Calculation.class));
    }

    public void confirm(Long documentId, Long executionId, String mode) {
        var document = find(documentId);
        if (document == null) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        if (!Mode.LOCAL_ONLY.getValue().equals(mode)
                || !Mode.LOCAL_ONLY.getValue().equals(document.getProductionSourceMode())
                || !Objects.equals(document.getSourceExecutionId(), executionId))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (document.getProductionConfirmedAt() != null) {
            if (!Boolean.TRUE.equals(document.getProductionStockApplied()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return;
        }
        var calculation = json.readValue(document.getProductionCalculationJson(), Calculation.class);
        if (!Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode()))
            ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        stockDocuments.getObject().postMaterialIssue(documentId, document, calculation);
        // Posting changes the canonical document state. Do not write the pre-post snapshot back over it.
        document = documents.selectByPrimaryKey(documentId);
        if (document == null || !StockDocumentType.MATERIAL_ISSUE.getValue().equals(document.getDocumentType())
                || !StockDocumentStatus.POSTED.getValue().equals(document.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        document.setProductionStockApplied(true);
        var confirmedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        document.setProductionConfirmedAt(confirmedAt);
        document.setProductionBusinessOccurredAt(confirmedAt);
        if (documents.updateByPrimaryKeyWithBLOBs(document) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public void voidConfirmed(Long documentId, String voidOperationId) {
        var document = find(documentId);
        if (document == null || voidOperationId == null
                || !Objects.equals(voidOperationId, document.getProductionVoidOperationId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (!Boolean.TRUE.equals(document.getProductionStockApplied())) {
            if ("SUCCEEDED".equals(document.getProductionVoidResultStatus())) return;
            var calculation = json.readValue(document.getProductionCalculationJson(), Calculation.class);
            if (!inventory.hasRecordedReversal(document, calculation, voidOperationId))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            document.setProductionVoidResultStatus("SUCCEEDED");
            if (documents.updateByPrimaryKeyWithBLOBs(document) != 1)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return;
        }
        if (!Mode.LOCAL_ONLY.getValue().equals(document.getProductionSourceMode())
                || document.getProductionConfirmedAt() == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        inventory.reverse(document, json.readValue(document.getProductionCalculationJson(), Calculation.class),
                voidOperationId);
        document.setProductionStockApplied(false);
        if (documents.updateByPrimaryKeyWithBLOBs(document) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public int updateVoid(WmStockDocumentEntity document) {
        return documents.updateByPrimaryKeyWithBLOBs(document);
    }
}
