package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Allocation;
import dev.mesbase.business.production.domain.ProductionInputKind;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Production contribution and original creation identity, associated with a stock document.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueInputFacts {
    public record CombinationInput(int schemaVersion, Long combinationId, Long workorderId, Long demandRevision,
                                   BigDecimal outputQty, List<Allocation> allocations) {
    }

    private final MesMaterialIssueInputMapper inputs;
    private final WmStockActionMapper actions;
    private final ProductionInputExtraMapper inputQueries;
    private final LocalStockService inventory;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueInputFacts(MesMaterialIssueInputMapper inputs, WmStockActionMapper actions,
                                   ProductionInputExtraMapper inputQueries, LocalStockService inventory) {
        this.inputs = inputs;
        this.actions = actions;
        this.inputQueries = inputQueries;
        this.inventory = inventory;
    }

    public MesMaterialIssueInputEntity find(Long documentId) {
        var filter = new MesMaterialIssueInputExample();
        filter.createCriteria().andDocumentIdEqualTo(documentId);
        var rows = inputs.selectByExampleWithBLOBs(filter);
        if (rows.size() > 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (rows.isEmpty()) return null;
        return rows.getFirst();
    }

    public MesMaterialIssueInputEntity findByRequestId(String requestId) {
        return inputs.selectByPrimaryKey(requestId);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public MesMaterialIssueInputEntity recordMesConfirmation(WmStockDocumentEntity document, String requestId,
                                                             String requestHash, Long executionId,
                                                             CombinationInput calculation) {
        if (!StockDocumentStatus.POSTED.getValue().equals(document.getStatus())
                || !StockDocumentType.MATERIAL_ISSUE.getValue().equals(document.getDocumentType())
                || !Objects.equals(document.getSourceExecutionId(), executionId)
                || !Objects.equals(document.getSourceWorkorderId(), calculation.workorderId())
                || calculation.outputQty().signum() <= 0 || calculation.allocations().isEmpty())
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var row = new MesMaterialIssueInputEntity();
        row.setId(requestId);
        row.setRequestHash(requestHash);
        row.setDocumentId(document.getId());
        row.setExecutionId(executionId);
        row.setInputKind(ProductionInputKind.PRODUCTION_INPUT.getValue());
        row.setSourceMode(Mode.LOCAL_ONLY.getValue());
        row.setOutputQty(calculation.outputQty());
        row.setCalculationJson(json.writeValueAsString(calculation));
        row.setStockApplied(true);
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        row.setCreatedAt(now);
        row.setBusinessOccurredAt(now);
        row.setConfirmedAt(now);
        if (inputs.insert(row) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return row;
    }

    public CombinationInput combinationSnapshot(Long documentId) {
        var input = find(documentId);
        if (input == null) return null;
        var tree = json.readTree(input.getCalculationJson());
        if (!tree.has("schemaVersion")) return null;
        var result = json.readValue(input.getCalculationJson(), CombinationInput.class);
        if (result.schemaVersion() != 2) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return result;
    }

    public record LocalInputSnapshot(String requestId, String inputKind, BigDecimal outputQty,
                                     Calculation calculation) {
    }

    public LocalInputSnapshot snapshot(Long documentId) {
        var document = find(documentId);
        if (document == null) return null;
        return new LocalInputSnapshot(document.getId(), document.getInputKind(),
                document.getOutputQty(),
                json.readValue(document.getCalculationJson(), Calculation.class));
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
        if (rows.isEmpty()) return null;
        var first = actions.selectByPrimaryKey(rows.getFirst());
        for (var id : rows) {
            var row = actions.selectByPrimaryKey(id);
            if (row == null || !Objects.equals(first.getOperationId(), row.getOperationId()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        if (first.getOperationId() != null) {
            var operation = actions.selectByPrimaryKey(first.getOperationId());
            if (operation != null) return operation;
        }
        return first;
    }

    public int updateVoid(MesMaterialIssueInputEntity input) {
        return inputs.updateByPrimaryKeyWithBLOBs(input);
    }
}
