package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionQuantityInput;

import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.shared.enums.BusinessSource;

import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;

/**
 * One local authority per workorder. A disabled database row never falls back to YAML.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkorderMaterialService {
    private final BusinessSourceRules sources;

    public record Save(Long expectedRevision, Long materialId, BigDecimal totalRequiredQty,
                       List<Long> warehouseIds, String workCenter, boolean enabled) {
    }

    private final StockReferenceRules references;
    private final MesWorkorderMaterialMapper requirements;
    private final MesMaterialIssueMapper inputs;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmWarehouseMapper warehouses;
    private final ProductionChainLocks locks;
    private final WarehouseIntegration integration;

    public WorkorderMaterialService(MesWorkorderMaterialMapper requirements, MesMaterialIssueMapper inputs,
                                    MaterialMapper materials, UnitMeasureMapper units, WmWarehouseMapper warehouses,
                                    ProductionChainLocks locks, WarehouseIntegration integration,
                                    StockReferenceRules references, BusinessSourceRules sources) {
        this.sources = sources;
        this.references = references;
        this.requirements = requirements;
        this.inputs = inputs;
        this.materials = materials;
        this.units = units;
        this.warehouses = warehouses;
        this.locks = locks;
        this.integration = integration;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesWorkorderMaterialEntity get(Long workorder) {
        return requirements.selectByPrimaryKey(workorder);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesWorkorderMaterialEntity save(Long workorder, Save c) {
        if (integration.configuredMode(FactoryContext.required()) != Mode.LOCAL_ONLY)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var order = locks.order(workorder);
        sources.order(workorder);
        if (!List.of(WorkOrderStatus.DRAFT.getValue(), WorkOrderStatus.RELEASED.getValue()).contains(order.getStatus()))
            fail();
        var f = new MesMaterialIssueExample();
        f.createCriteria().andWorkorderIdEqualTo(workorder);
        // Includes prepared/UNKNOWN intents: their frozen ratio must remain recoverable.
        if (inputs.countByExample(f) > 0) throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        var previous = requirements.selectByPrimaryKey(workorder);
        long revision = previous == null ? 0 : previous.getRevision();
        if (c == null || c.expectedRevision() == null || c.expectedRevision() != revision || c.materialId() == null
                || c.totalRequiredQty() == null || c.totalRequiredQty().signum() <= 0 || c.totalRequiredQty()
                .scale() > 6
                || c.totalRequiredQty().precision() - c.totalRequiredQty().scale() > 14 || c.workCenter() == null
                || c.workCenter().isBlank() || c.workCenter().length() > 100 || c.warehouseIds() == null
                || c.warehouseIds().isEmpty() || c.warehouseIds().size() > 100 || c.warehouseIds().stream()
                .anyMatch(Objects::isNull)) fail();
        ProductionQuantityInput.changed(c.totalRequiredQty(), previous == null ? null : previous.getTotalRequiredQty());
        var ids = c.warehouseIds().stream().distinct().sorted().toList();
        references.lockDemandReferences(c.materialId(), ids);
        sources.warehouses(order.getSource(), ids);
        var material = materials.selectByPrimaryKey(c.materialId());
        var unit = material == null || material.getUnitId() == null ? null : units.selectByPrimaryKey(material.getUnitId());
        if (material == null || unit == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())) fail();
        var wf = new WmWarehouseExample();
        wf.createCriteria().andIdIn(ids).andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (warehouses.countByExample(wf) != ids.size()) fail();
        var row = new MesWorkorderMaterialEntity();
        row.setId(workorder);
        row.setMaterialId(c.materialId());
        row.setUnitId(unit.getId());
        row.setTotalRequiredQty(c.totalRequiredQty());
        row.setPlannedQty(order.getPlannedQty());
        String allowed = new ObjectMapper().writeValueAsString(ids);
        if (allowed.length() > 2000) fail();
        row.setWarehouseIds(allowed);
        row.setWorkCenter(c.workCenter().trim());
        row.setRevision(revision + 1);
        row.setStatus(c.enabled() ? EnableStatus.ENABLED.getValue() : EnableStatus.DISABLED.getValue());
        row.setUpdatedBy(SecurityUtils.getUserId());
        row.setUpdatedAt(LocalDateTime.now());
        if ((previous == null ? requirements.insert(row) : requirements.updateByPrimaryKey(row)) != 1) fail();
        return row;
    }

    private static void fail() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
