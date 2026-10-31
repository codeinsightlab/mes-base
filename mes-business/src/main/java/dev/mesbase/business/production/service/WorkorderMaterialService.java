package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialBomDirect;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.production.domain.ProductionInputQuota;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderMaterialModels;
import dev.mesbase.business.production.domain.WorkorderMaterialModels.Save;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Work-order configuration owns its direct material lines; stock allocation belongs to issue documents.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkorderMaterialService {
    private final BusinessSourceRules sources;
    private final MaterialBomMapper boms;
    private final MaterialBomService bomService;
    private final ProductionInputExtraMapper quotas;
    private final StockReferenceRules references;
    private final MesWorkorderMaterialMapper requirements;
    private final WorkorderMapper orders;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmWarehouseMapper warehouses;
    private final ProductionChainLocks locks;
    private final WarehouseIntegration integration;
    private final MaterialQuantityRules quantities;
    private final AuthorizationService authorization;

    public WorkorderMaterialService(MesWorkorderMaterialMapper requirements, WorkorderMapper orders,
                                    MaterialMapper materials, UnitMeasureMapper units, WmWarehouseMapper warehouses,
                                    ProductionChainLocks locks, WarehouseIntegration integration,
                                    StockReferenceRules references, BusinessSourceRules sources, MaterialBomMapper boms,
                                    MaterialBomService bomService, ProductionInputExtraMapper quotas,
                                    MaterialQuantityRules quantities, AuthorizationService authorization) {
        this.boms = boms;
        this.bomService = bomService;
        this.quotas = quotas;
        this.sources = sources;
        this.references = references;
        this.requirements = requirements;
        this.orders = orders;
        this.materials = materials;
        this.units = units;
        this.warehouses = warehouses;
        this.locks = locks;
        this.integration = integration;
        this.quantities = quantities;
        this.authorization = authorization;
    }

    public void requireUpdatePermission() {
        if (!authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                FactoryContext.required(), new Permission("workorder:material:update")))
            ParameterChecks.reject(ErrorCode.FORBIDDEN);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkorderMaterialModels.Document get(Long workorder) {
        var order = orders.selectByPrimaryKey(workorder);
        if (order == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        var rows = lines(workorder);
        return new WorkorderMaterialModels.Document(workorder, order.getBomId(), order.getBomRevision(),
                order.getMaterialRevision(), order.getMaterialWarehouseIds(), order.getMaterialWorkCenter(),
                order.getMaterialStatus(), order.getMaterialFrozenAt(), order.getMaterialUpdatedAt(), rows.stream()
                .map(row -> new WorkorderMaterialModels.Item(row.getId(), row.getBomItemId(), row.getMaterialId(),
                        row.getUnitId(), row.getTotalRequiredQty(), standard(row), row.getBomBaseQty(),
                        row.getBomComponentQty(), row.getPlannedQty(), row.getOutputUnitId(),
                        Boolean.TRUE.equals(row.getManuallyAdjusted()))).toList());
    }

    private static BigDecimal standard(MesWorkorderMaterialEntity row) {
        if (row.getBomItemId() == null || row.getBomBaseQty() == null || row.getBomComponentQty() == null
                || row.getBomBaseQty().signum() <= 0 || row.getBomComponentQty().signum() <= 0) return null;
        try {
            return ProductionInputQuota.materialFor(row.getPlannedQty(), row.getBomBaseQty(), row.getBomComponentQty());
        } catch (FoundationException failure) {
            // Only the pure quantity projection can be unrepresentable; original demand remains readable.
            if (failure.code() == ErrorCode.BAD_REQUEST) return null;
            throw failure;
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public WorkorderMaterialModels.Document save(Long workorder, Save command) {
        var order = locks.order(workorder);
        order.setRevision(order.getRevision() + 1);
        replace(order, command);
        return get(workorder);
    }

    /**
     * Caller owns the work-order lock and transaction; aggregate failure rolls back the header and all lines.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void replace(WorkorderEntity order, Save command) {
        if (integration.configuredMode(FactoryContext.required()) != Mode.LOCAL_ONLY)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        sources.order(order.getId());
        if (!List.of(WorkOrderStatus.DRAFT.getValue(), WorkOrderStatus.RELEASED.getValue()).contains(order.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (order.getMaterialFrozenAt() != null || quotas.hasFormalInput(order.getId()))
            throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        if (command == null || command.expectedRevision() == null
                || !Objects.equals(command.expectedRevision(), order.getMaterialRevision())
                || command.bomId() == null || command.expectedBomRevision() == null
                || command.warehouseIds() == null || command.warehouseIds().size() > 100
                || command.warehouseIds().stream().anyMatch(Objects::isNull)
                || command.workCenter() == null || command.workCenter().length() > 100)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var previous = lines(order.getId());
        boolean basisChanged = !Objects.equals(command.bomId(), order.getBomId()) || previous.stream()
                .anyMatch(row -> row.getPlannedQty().compareTo(order.getPlannedQty()) != 0);
        if (basisChanged && previous.stream().anyMatch(row -> Boolean.TRUE.equals(row.getManuallyAdjusted()))
                && !Boolean.TRUE.equals(command.adjustmentsConfirmed()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var input = command.items();
        if (input != null && (input.isEmpty() || input.size() > 100 || input.stream().anyMatch(Objects::isNull)))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var direct = bomService.direct(command.bomId(), order.getPlannedQty());
        var materialIds = new ArrayList<Long>();
        materialIds.add(order.getProductId());
        materialIds.addAll(direct.items().stream().map(MaterialBomDirect.Component::materialId).toList());
        if (input != null) materialIds.addAll(input.stream().map(WorkorderMaterialModels.Line::materialId).toList());
        if (materialIds.stream().anyMatch(Objects::isNull)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        // At most 100 lines; use the existing sorted material/unit lock protocol before locking the BOM.
        quantities.lockForWrite(materialIds);
        var selected = boms.selectByPrimaryKeyForUpdate(command.bomId());
        if (selected == null || !Objects.equals(selected.getProductId(), order.getProductId())
                || !Objects.equals(selected.getRevision(), command.expectedBomRevision()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var components = direct.items().stream()
                .collect(Collectors.toMap(MaterialBomDirect.Component::lineId, Function.identity()));
        var previousComponents = previous.stream().filter(row -> row.getBomItemId() != null)
                .collect(Collectors.toMap(MesWorkorderMaterialEntity::getBomItemId, Function.identity()));
        if (input == null) {
            if (previous.stream().anyMatch(row -> Boolean.TRUE.equals(row.getManuallyAdjusted())))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            input = direct.items().stream().map(component -> {
                Long existingId = null;
                var existing = previousComponents.get(component.lineId());
                if (existing != null) existingId = existing.getId();
                return new WorkorderMaterialModels.Line(existingId, component.lineId(), component.materialId(),
                        component.unitId(), component.quantity(), false);
            }).toList();
        }
        var prior = previous.stream().collect(Collectors.toMap(MesWorkorderMaterialEntity::getId, Function.identity()));
        var seenIds = new HashSet<Long>();
        var seenComponents = new HashSet<Long>();
        var prepared = new ArrayList<MesWorkorderMaterialEntity>();
        int lineNo = 0;
        for (var line : input) {
            if (line.materialId() == null || line.unitId() == null || line.totalRequiredQty() == null
                    || Objects.equals(line.materialId(), order.getProductId()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            var row = new MesWorkorderMaterialEntity();
            if (line.id() != null) {
                if (!seenIds.add(line.id()) || !prior.containsKey(line.id()))
                    ParameterChecks.reject(ErrorCode.BAD_REQUEST);
                row = prior.get(line.id());
            }
            var component = components.get(line.bomItemId());
            if (line.bomItemId() != null && (component == null || !seenComponents.add(line.bomItemId())))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            if (!line.manuallyAdjusted() && (component == null
                    || !Objects.equals(component.materialId(), line.materialId())
                    || !Objects.equals(component.unitId(), line.unitId())
                    || component.quantity().compareTo(line.totalRequiredQty()) != 0))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            quantities.checkForWrite(line.totalRequiredQty(), line.materialId());
            var material = materials.selectByPrimaryKey(line.materialId());
            var unit = units.selectByPrimaryKey(line.unitId());
            if (material == null || unit == null || !Objects.equals(material.getUnitId(), line.unitId())
                    || !EnableStatus.ENABLED.getValue().equals(material.getStatus())
                    || !EnableStatus.ENABLED.getValue().equals(unit.getStatus()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            row.setWorkorderId(order.getId());
            row.setLineNo(++lineNo);
            row.setBomItemId(line.bomItemId());
            row.setMaterialId(line.materialId());
            row.setUnitId(line.unitId());
            row.setTotalRequiredQty(line.totalRequiredQty());
            row.setPlannedQty(order.getPlannedQty());
            row.setManuallyAdjusted(line.manuallyAdjusted() || component == null);
            row.setOutputUnitId(selected.getUnitId());
            row.setBomBaseQty(null);
            row.setBomComponentQty(null);
            if (component != null) {
                row.setBomBaseQty(component.baseQty());
                row.setBomComponentQty(component.componentQty());
            }
            row.setUpdatedBy(SecurityUtils.getUserId());
            row.setUpdatedAt(LocalDateTime.now());
            prepared.add(row);
        }
        if (!seenComponents.equals(components.keySet())) ParameterChecks.reject(ErrorCode.MATERIAL_BOM_INVALID);
        var warehouseIds = command.warehouseIds().stream().distinct().sorted().toList();
        if (!warehouseIds.isEmpty()) {
            references.lockDemandReferences(order.getProductId(), warehouseIds);
            sources.warehouses(order.getSource(), warehouseIds);
            var filter = new WmWarehouseExample();
            filter.createCriteria().andIdIn(warehouseIds).andStatusEqualTo(EnableStatus.ENABLED.getValue());
            if (warehouses.countByExample(filter) != warehouseIds.size())
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        String allowed = new ObjectMapper().writeValueAsString(warehouseIds);
        if (allowed.length() > 2000) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        order.setBomId(command.bomId());
        order.setBomRevision(command.expectedBomRevision());
        order.setMaterialWarehouseIds(allowed);
        order.setMaterialWorkCenter(command.workCenter().trim());
        order.setMaterialStatus(command.enabled() ? EnableStatus.ENABLED.getValue() : EnableStatus.DISABLED.getValue());
        order.setMaterialRevision(order.getMaterialRevision() + 1);
        order.setMaterialUpdatedBy(SecurityUtils.getUserId());
        order.setMaterialUpdatedAt(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        if (orders.updateByPrimaryKey(order) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var removed = previous.stream().map(MesWorkorderMaterialEntity::getId).filter(id -> !seenIds.contains(id))
                .toList();
        if (!removed.isEmpty()) {
            var filter = new MesWorkorderMaterialExample();
            filter.createCriteria().andWorkorderIdEqualTo(order.getId()).andIdIn(removed);
            if (requirements.deleteByExample(filter) != removed.size())
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        // Bounded by the 100-line command limit; generated Mapper writes stay in this one transaction.
        for (var row : prepared) {
            int changed = row.getId() == null ? requirements.insert(row) : requirements.updateByPrimaryKey(row);
            if (changed != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    public List<MesWorkorderMaterialEntity> lines(Long workorder) {
        var filter = new MesWorkorderMaterialExample();
        filter.createCriteria().andWorkorderIdEqualTo(workorder);
        filter.setOrderByClause("line_no ASC,id ASC");
        return requirements.selectByExample(filter);
    }

    /**
     * Unsupported multi-line or adjusted demand must never be reduced to a first-row issue.
     */
    public WorkorderMaterialModels.Single basis(WorkorderEntity order) {
        var rows = lines(order.getId());
        if (rows.isEmpty()) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (rows.size() > 1) ParameterChecks.reject(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED);
        var row = rows.getFirst();
        if (Boolean.TRUE.equals(row.getManuallyAdjusted()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_ADJUSTED_MATERIAL_PENDING);
        if (order.getBomId() == null || !EnableStatus.ENABLED.getValue().equals(order.getMaterialStatus())
                || row.getPlannedQty().compareTo(order.getPlannedQty()) != 0
                || row.getBomBaseQty() == null || row.getBomComponentQty() == null || row.getOutputUnitId() == null
                || row.getBomBaseQty().signum() <= 0 || row.getBomComponentQty().signum() <= 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (order.getMaterialFrozenAt() == null) {
            var direct = bomService.direct(order.getBomId(), order.getPlannedQty());
            if (direct.items().size() != 1) ParameterChecks.reject(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED);
            var component = direct.items().getFirst();
            if (!Objects.equals(component.materialId(), row.getMaterialId())
                    || !Objects.equals(component.unitId(), row.getUnitId())
                    || !Objects.equals(direct.version().getUnitId(), row.getOutputUnitId())
                    || component.baseQty().compareTo(row.getBomBaseQty()) != 0
                    || component.componentQty().compareTo(row.getBomComponentQty()) != 0
                    || component.quantity().compareTo(row.getTotalRequiredQty()) != 0)
                ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        }
        return new WorkorderMaterialModels.Single(order.getId(), order.getBomId(), row.getMaterialId(), row.getUnitId(),
                row.getTotalRequiredQty(), row.getPlannedQty(), row.getBomBaseQty(), row.getBomComponentQty(),
                row.getOutputUnitId(), order.getMaterialWarehouseIds(), order.getMaterialWorkCenter(),
                order.getMaterialRevision(), order.getMaterialFrozenAt());
    }

    public BigDecimal issueQuantity(WorkorderEntity order, BigDecimal output) {
        var row = basis(order);
        return quotas.quota(order.getId(), row.materialId(), row.unitId())
                .next(order.getPlannedQty(), output, row.bomBaseQty(), row.bomComponentQty());
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void freeze(Calculation calculation) {
        var order = locks.order(calculation.order().workorderId());
        materials.selectByPrimaryKeyForUpdate(order.getProductId());
        boms.selectByPrimaryKeyForUpdate(order.getBomId());
        var row = basis(order);
        var basis = calculation.basis();
        if (basis == null || !Objects.equals(basis.bomId(), row.bomId())
                || basis.baseQty().compareTo(row.bomBaseQty()) != 0
                || basis.componentQty().compareTo(row.bomComponentQty()) != 0
                || !Objects.equals(basis.outputUnitId(), row.outputUnitId())
                || !Objects.equals(calculation.stock().materialId(), row.materialId())
                || !Objects.equals(calculation.stock().unitId(), row.unitId())
                || issueQuantity(order, calculation.outputQty()).compareTo(calculation.issueQty()) != 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (order.getMaterialFrozenAt() == null) {
            order.setMaterialFrozenAt(LocalDateTime.now());
            if (orders.updateByPrimaryKey(order) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
    }
}
