package dev.mesbase.business.issue.service;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand.Input;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.business.wm.domain.WmInventoryResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Frozen per-issue input; caller holds the common execution lock and transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueInputFacts {
    private final MesMaterialIssueInputMapper inputs;
    private final WmInventoryMapper inventory;
    private final ObjectMapper json = new ObjectMapper();

    public MaterialIssueInputFacts(MesMaterialIssueInputMapper inputs, WmInventoryMapper inventory) {
        this.inputs = inputs;
        this.inventory = inventory;
    }

    public MesMaterialIssueInputEntity find(Long issueId) {
        var f = new MesMaterialIssueInputExample();
        f.createCriteria().andIssueIdEqualTo(issueId);
        var rows = inputs.selectByExampleWithBLOBs(f);
        if (rows.size() > 1) fail();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Input snapshot(Long issueId) {
        var f = find(issueId);
        return f == null ? null : new Input(f.getId(), f.getInputKind(), f.getOutputQty(), json.readValue(f.getCalculationJson(), Calculation.class));
    }

    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void confirm(MesMaterialIssueEntity issue, String mode) {
        var fact = find(issue.getId());
        if (fact == null) return; // Historical/administrative issue has no fabricated output contribution.
        if (!Objects.equals(mode, fact.getSourceMode()) || !Objects.equals(fact.getExecutionId(), issue.getExecutionId()))
            fail();
        if (fact.getConfirmedAt() != null) return;
        if (Mode.LOCAL_ONLY.getValue().equals(mode)) {
            var calculation = json.readValue(fact.getCalculationJson(), Calculation.class);
            var sf = new WmInventoryExample();
            sf.createCriteria().andIdEqualTo(fact.getStockId());
            var lock = new WmInventoryEntity();
            lock.setId(fact.getStockId());
            if (inventory.updateByExampleSelective(lock, sf) != 1) fail();
            var stock = inventory.selectByPrimaryKey(fact.getStockId());
            var source = calculation.stock();
            var qty = calculation.issueQty();
            if (!EnableStatus.ENABLED.getValue()
                    .equals(stock.getStatus()) || !Objects.equals(stock.getMaterialId(), source.materialId())
                    || !Objects.equals(stock.getWarehouseId(), source.warehouseId()) || !Objects.equals(stock.getBatchNo(), source.lotCode())
                    || !Objects.equals(stock.getBinCode(), source.binCode()) || stock.getQuantity().compareTo(qty) < 0
                    || stock.getAvailableQuantity() != null && stock.getAvailableQuantity().compareTo(qty) < 0) fail();
            stock.setQuantity(stock.getQuantity().subtract(qty));
            if (stock.getAvailableQuantity() != null)
                stock.setAvailableQuantity(stock.getAvailableQuantity().subtract(qty));
            if (inventory.updateByPrimaryKey(stock) != 1) fail();
            fact.setStockApplied(true);
        }
        fact.setConfirmedAt(LocalDateTime.now());
        if (inputs.updateByPrimaryKeyWithBLOBs(fact) != 1) fail();
    }

    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void voidConfirmed(MesMaterialIssueEntity issue) {
        var fact = find(issue.getId());
        if (fact == null || !Boolean.TRUE.equals(fact.getStockApplied())) return;
        var sf = new WmInventoryExample();
        sf.createCriteria().andIdEqualTo(fact.getStockId());
        var patch = new WmInventoryEntity();
        patch.setId(fact.getStockId());
        if (inventory.updateByExampleSelective(patch, sf) != 1) fail();
        var stock = inventory.selectByPrimaryKey(fact.getStockId());
        var c = json.readValue(fact.getCalculationJson(), Calculation.class);
        if (!Objects.equals(stock.getMaterialId(), c.stock()
                .materialId()) || !Objects.equals(stock.getWarehouseId(), c.stock()
                .warehouseId()) || !Objects.equals(stock.getBatchNo(), c.stock().lotCode())) fail();
        stock.setQuantity(stock.getQuantity().add(c.issueQty()));
        if (stock.getAvailableQuantity() != null)
            stock.setAvailableQuantity(stock.getAvailableQuantity().add(c.issueQty()));
        if (inventory.updateByPrimaryKey(stock) != 1) fail();
        fact.setStockApplied(false);
        if (inputs.updateByPrimaryKeyWithBLOBs(fact) != 1) fail();
    }

    private static void fail() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
