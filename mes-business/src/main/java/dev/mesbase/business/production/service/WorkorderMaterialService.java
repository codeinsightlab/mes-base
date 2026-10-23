package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialBomItemMapper;
import dev.mesbase.business.basic.domain.MaterialBomItemExample;
import dev.mesbase.business.basic.domain.MaterialBomStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.ProductionInputQuota;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * One local authority per workorder. A disabled database row never falls back to YAML.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkorderMaterialService {
    private final BusinessSourceRules sources;

    public record Save(Long expectedRevision, Long bomId,
                       List<Long> warehouseIds, String workCenter, boolean enabled) {
    }

    private final MaterialBomMapper boms;
    private final MaterialBomItemMapper components;
    private final ProductionInputExtraMapper quotas;
    private final StockReferenceRules references;
    private final MesWorkorderMaterialMapper requirements;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmWarehouseMapper warehouses;
    private final ProductionChainLocks locks;
    private final WarehouseIntegration integration;

    public WorkorderMaterialService(MesWorkorderMaterialMapper requirements,
                                    MaterialMapper materials, UnitMeasureMapper units, WmWarehouseMapper warehouses,
                                    ProductionChainLocks locks, WarehouseIntegration integration,
                                    StockReferenceRules references, BusinessSourceRules sources, MaterialBomMapper boms,
                                    MaterialBomItemMapper components, ProductionInputExtraMapper quotas) {
        this.boms = boms;
        this.components = components;
        this.quotas = quotas;
        this.sources = sources;
        this.references = references;
        this.requirements = requirements;
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
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (quotas.hasFormalInput(workorder)) throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        var previous = requirements.selectByPrimaryKey(workorder);
        long revision = previous == null ? 0 : previous.getRevision();
        if (c == null || c.expectedRevision() == null || c.expectedRevision() != revision || c.bomId() == null
                || StringUtils.isBlank(c.workCenter()) || c.workCenter().length() > 100 || c.warehouseIds() == null
                || c.warehouseIds().isEmpty() || c.warehouseIds().size() > 100
                || c.warehouseIds().stream().anyMatch(Objects::isNull))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var row = new MesWorkorderMaterialEntity();
        row.setId(workorder);
        row.setBomId(c.bomId());
        materials.selectByPrimaryKeyForUpdate(order.getProductId());
        boms.selectByPrimaryKeyForUpdate(c.bomId());
        applyBom(row, order, true);
        var ids = c.warehouseIds().stream().distinct().sorted().toList();
        references.lockDemandReferences(row.getMaterialId(), ids);
        sources.warehouses(order.getSource(), ids);
        var material = materials.selectByPrimaryKey(row.getMaterialId());
        if (material == null || material.getUnitId() == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var unit = units.selectByPrimaryKey(material.getUnitId());
        if (material == null || unit == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(unit.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var wf = new WmWarehouseExample();
        wf.createCriteria().andIdIn(ids).andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (warehouses.countByExample(wf) != ids.size()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        String allowed = new ObjectMapper().writeValueAsString(ids);
        if (allowed.length() > 2000) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        row.setWarehouseIds(allowed);
        row.setWorkCenter(c.workCenter().trim());
        row.setRevision(revision + 1);
        row.setStatus(c.enabled() ? EnableStatus.ENABLED.getValue() : EnableStatus.DISABLED.getValue());
        row.setUpdatedBy(SecurityUtils.getUserId());
        row.setUpdatedAt(LocalDateTime.now());
        if ((previous == null ? requirements.insert(row) : requirements.updateByPrimaryKey(row)) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return row;
    }

    /**
     * The selected version remains replaceable after release, until a formal input intent is committed.
     */
    public MesWorkorderMaterialEntity basis(WorkorderEntity order) {
        var row = requirements.selectByPrimaryKey(order.getId());
        if (row == null || row.getBomId() == null || !EnableStatus.ENABLED.getValue().equals(row.getStatus()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (row.getFrozenAt() == null) applyBom(row, order, true);
        else if (row.getPlannedQty().compareTo(order.getPlannedQty()) != 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (row.getBomBaseQty() == null || row.getBomComponentQty() == null || row.getOutputUnitId() == null
                || row.getBomBaseQty().signum() <= 0 || row.getBomComponentQty().signum() <= 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        return row;
    }

    public BigDecimal issueQuantity(WorkorderEntity order, BigDecimal output) {
        var row = basis(order);
        return quotas.quota(order.getId(), row.getMaterialId(), row.getUnitId())
                .next(order.getPlannedQty(), output, row.getBomBaseQty(), row.getBomComponentQty());
    }

    /**
     * Caller holds the work-order lock. LOCAL calls this in the same transaction as stock and Execution.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void freeze(Calculation calculation) {
        var order = locks.order(calculation.order().workorderId());
        var selected = requirements.selectByPrimaryKey(order.getId());
        if (selected == null || selected.getBomId() == null) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        materials.selectByPrimaryKeyForUpdate(order.getProductId());
        boms.selectByPrimaryKeyForUpdate(selected.getBomId());
        var row = basis(order);
        var basis = calculation.basis();
        if (basis == null || !Objects.equals(basis.bomId(), row.getBomId())
                || basis.baseQty().compareTo(row.getBomBaseQty()) != 0
                || basis.componentQty().compareTo(row.getBomComponentQty()) != 0
                || !Objects.equals(basis.outputUnitId(), row.getOutputUnitId())
                || !Objects.equals(calculation.stock().materialId(), row.getMaterialId())
                || !Objects.equals(calculation.stock().unitId(), row.getUnitId())
                || issueQuantity(order, calculation.outputQty()).compareTo(calculation.issueQty()) != 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (row.getFrozenAt() == null) {
            row.setFrozenAt(LocalDateTime.now());
            if (requirements.updateByPrimaryKey(row) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private void applyBom(MesWorkorderMaterialEntity row, WorkorderEntity order, boolean requirePublished) {
        var bom = boms.selectByPrimaryKey(row.getBomId());
        if (bom == null || !Objects.equals(bom.getProductId(), order.getProductId())
                || requirePublished && !MaterialBomStatus.PUBLISHED.getValue().equals(bom.getStatus()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var f = new MaterialBomItemExample();
        f.createCriteria().andBomIdEqualTo(bom.getId());
        if (components.countByExample(f) != 1)
            ParameterChecks.reject(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED);
        var component = components.selectByExample(f).getFirst();
        if (Objects.equals(component.getMaterialId(), order.getProductId()) || component.getQuantity() == null
                || component.getQuantity().signum() <= 0 || bom.getBaseQty() == null || bom.getBaseQty().signum() <= 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var product = materials.selectByPrimaryKey(order.getProductId());
        var material = materials.selectByPrimaryKey(component.getMaterialId());
        if (product == null || material == null || !Objects.equals(bom.getUnitId(), product.getUnitId())
                || !Objects.equals(component.getUnitId(), material.getUnitId())
                || !EnableStatus.ENABLED.getValue().equals(product.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(material.getStatus()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        row.setMaterialId(component.getMaterialId());
        row.setUnitId(component.getUnitId());
        row.setOutputUnitId(bom.getUnitId());
        row.setBomBaseQty(bom.getBaseQty());
        row.setBomComponentQty(component.getQuantity());
        row.setPlannedQty(order.getPlannedQty());
        row.setTotalRequiredQty(ProductionInputQuota.materialFor(order.getPlannedQty(), bom.getBaseQty(), component.getQuantity()));
    }

}
