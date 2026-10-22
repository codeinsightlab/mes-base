package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand.Input;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Frozen per-issue input; caller holds the common execution lock and transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueInputFacts {
    private final MesMaterialIssueInputMapper inputs;
    private final LocalStockService inventory;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueInputFacts(MesMaterialIssueInputMapper inputs, LocalStockService inventory) {
        this.inputs = inputs;
        this.inventory = inventory;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void freeze(MesMaterialIssueInputEntity fact, Calculation calculation) {
        if (Mode.LOCAL_ONLY.getValue().equals(fact.getSourceMode())) inventory.validateInputSource(fact, calculation);
        if (inputs.insert(fact) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public MesMaterialIssueInputEntity find(Long issueId) {
        var f = new MesMaterialIssueInputExample();
        f.createCriteria().andIssueIdEqualTo(issueId);
        var rows = inputs.selectByExampleWithBLOBs(f);
        if (rows.size() > 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Input snapshot(Long issueId) {
        var f = find(issueId);
        if (f == null) return null;
        return new Input(f.getId(), f.getInputKind(), f.getOutputQty(), json.readValue(f.getCalculationJson(), Calculation.class));
    }

    public void confirm(MesMaterialIssueEntity issue, String mode) {
        var fact = find(issue.getId());
        if (fact == null) return; // Historical/administrative issue has no fabricated output contribution.
        if (!Objects.equals(mode, fact.getSourceMode()) || !Objects.equals(fact.getExecutionId(), issue.getExecutionId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (fact.getConfirmedAt() != null) return;
        if (Mode.LOCAL_ONLY.getValue().equals(mode)) {
            var calculation = json.readValue(fact.getCalculationJson(), Calculation.class);
            inventory.issue(fact, calculation);
            fact.setStockApplied(true);
        }
        fact.setConfirmedAt(LocalDateTime.now());
        if (inputs.updateByPrimaryKeyWithBLOBs(fact) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    public void voidConfirmed(MesMaterialIssueEntity issue) {
        var fact = find(issue.getId());
        if (fact == null || !Boolean.TRUE.equals(fact.getStockApplied())) return;
        if (!Mode.LOCAL_ONLY.getValue().equals(fact.getSourceMode()) || fact.getConfirmedAt() == null)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        inventory.reverse(fact, json.readValue(fact.getCalculationJson(), Calculation.class), issue.getVoidOperationId());
        fact.setStockApplied(false);
        if (inputs.updateByPrimaryKeyWithBLOBs(fact) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

}
