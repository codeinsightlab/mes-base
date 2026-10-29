package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
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

/** Production contribution and original creation identity, associated with a stock document. */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueInputFacts {
    private final WmStockDocumentMapper documents;
    private final MesMaterialIssueInputMapper inputs;
    private final WmStockActionMapper actions;
    private final ProductionInputExtraMapper inputQueries;
    private final LocalStockService inventory;
    private final ObjectProvider<StockDocumentService> stockDocuments;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueInputFacts(WmStockDocumentMapper documents, MesMaterialIssueInputMapper inputs, WmStockActionMapper actions, ProductionInputExtraMapper inputQueries, LocalStockService inventory,
                                   ObjectProvider<StockDocumentService> stockDocuments) {
        this.documents = documents;
        this.inputs = inputs;
        this.actions = actions;
        this.inputQueries = inputQueries;
        this.inventory = inventory;
        this.stockDocuments = stockDocuments;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void freeze(WmStockDocumentEntity document, MesMaterialIssueInputEntity input, Calculation calculation) {
        if (input.getId() == null || input.getRequestHash() == null || input.getExecutionId() == null
                || !Objects.equals(document.getId(), input.getDocumentId())
                || !Mode.LOCAL_ONLY.getValue().equals(input.getSourceMode())
                || !Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode()))
            ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        // Source validation reads the canonical document; publish its execution link inside this
        // transaction before validating, so a brand-new input is not compared against a stale draft.
        if (documents.updateByPrimaryKeyWithBLOBs(document) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        inventory.validateInputSource(input, calculation);
        if (inputs.insert(input) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public MesMaterialIssueInputEntity find(Long documentId) {
        var filter = new MesMaterialIssueInputExample();
        filter.createCriteria().andDocumentIdEqualTo(documentId);
        var rows = inputs.selectByExampleWithBLOBs(filter);
        if (rows.size() > 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public MesMaterialIssueInputEntity findByRequestId(String requestId) {
        return inputs.selectByPrimaryKey(requestId);
    }

    public record LocalInputSnapshot(String requestId, String inputKind, BigDecimal outputQty,
                                     Calculation calculation) { }

    public LocalInputSnapshot snapshot(Long documentId) {
        var document = find(documentId);
        if (document == null) return null;
        return new LocalInputSnapshot(document.getId(), document.getInputKind(),
                document.getOutputQty(),
                json.readValue(document.getCalculationJson(), Calculation.class));
    }

    public void confirm(Long documentId, Long executionId, String mode, String postRequestId) {
        var document = find(documentId);
        if (document == null) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        if (!Mode.LOCAL_ONLY.getValue().equals(mode)
                || !Mode.LOCAL_ONLY.getValue().equals(document.getSourceMode())
                || !Objects.equals(document.getExecutionId(), executionId))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (document.getConfirmedAt() != null) {
            if (!Boolean.TRUE.equals(document.getStockApplied()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return;
        }
        var calculation = json.readValue(document.getCalculationJson(), Calculation.class);
        if (!Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode()))
            ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        stockDocuments.getObject().postMaterialIssue(documentId, document, calculation, postRequestId);
        // Posting changes the canonical document state. Do not write the pre-post snapshot back over it.
        var posted = documents.selectByPrimaryKey(documentId);
        if (posted == null || !StockDocumentType.MATERIAL_ISSUE.getValue().equals(posted.getDocumentType())
                || !StockDocumentStatus.POSTED.getValue().equals(posted.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        document.setStockApplied(true);
        var confirmedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        document.setConfirmedAt(confirmedAt);
        if (inputs.updateByPrimaryKeyWithBLOBs(document) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public void voidConfirmed(Long documentId, String voidOperationId) {
        var input = find(documentId);
        if (input == null || voidOperationId == null || !Mode.LOCAL_ONLY.getValue().equals(input.getSourceMode())
                || input.getConfirmedAt() == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        // The caller has locked/re-read the input and excluded its contribution in this transaction.
        inventory.reverse(input, voidOperationId);
        input.setStockApplied(false);
        if (inputs.updateByPrimaryKeyWithBLOBs(input) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public WmStockActionEntity recordedReversal(Long documentId) {
        var input = find(documentId);
        if (input == null) return null;
        var rows = inputQueries.reversalIds(documentId);
        if (rows.size() > 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return rows.isEmpty() ? null : actions.selectByPrimaryKey(rows.getFirst());
    }

    public int updateVoid(MesMaterialIssueInputEntity input) {
        return inputs.updateByPrimaryKeyWithBLOBs(input);
    }
}
