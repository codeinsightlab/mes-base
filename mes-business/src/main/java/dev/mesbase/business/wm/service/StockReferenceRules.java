package dev.mesbase.business.wm.service;

import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.sync.ExternalWmInventory;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.stream.Collectors;
import dev.mesbase.business.wm.domain.WmInventoryItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * EXTRA_CODE_REASON: current CRUD generator has no cross-table reference/deletion rules.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class StockReferenceRules {
    private final MesWorkorderMaterialMapper demand;
    private final WmStockDocumentLineMapper documentLines;
    private final MaterialMapper materials;
    private final WmWarehouseMapper warehouses;
    private final WmInventoryMapper inventories;
    private final WmStockActionMapper actions;
    private final MesMaterialIssueInputMapper inputs;

    public StockReferenceRules(MaterialMapper materials, WmWarehouseMapper warehouses, WmInventoryMapper inventories,
                               WmStockActionMapper actions, MesMaterialIssueInputMapper inputs,
                               MesWorkorderMaterialMapper demand, WmStockDocumentLineMapper documentLines) {
        this.documentLines = documentLines;
        this.demand = demand;
        this.actions = actions;
        this.inputs = inputs;
        this.materials = materials;
        this.warehouses = warehouses;
        this.inventories = inventories;
    }

    /**
     * Caller holds a named business write transaction.
     */
    public WmInventoryEntity lockInventory(Long id) {
        var filter = new WmInventoryExample();
        filter.createCriteria().andIdEqualTo(id);
        var patch = new WmInventoryEntity();
        patch.setId(id);
        if (id == null || inventories.updateByExampleSelective(patch, filter) != 1)
            throw new FoundationException(ErrorCode.NOT_FOUND);
        return inventories.selectByPrimaryKey(id);
    }

    public boolean inventoryReferenced(Long id) {
        var action = new WmStockActionExample();
        action.createCriteria().andStockIdEqualTo(id);
        var input = new MesMaterialIssueInputExample();
        input.createCriteria().andStockIdEqualTo(id);
        var document = new WmStockDocumentLineExample();
        document.createCriteria().andStockIdEqualTo(id);
        return actions.countByExample(action) > 0 || inputs.countByExample(input) > 0
                || documentLines.countByExample(document) > 0;
    }

    private void status(String status) {
        if (status == null || EnableStatus.from(status) == null)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public void validate(WmWarehouseEntity row) {
        status(row.getStatus());
    }

    public void validateInventoryFields(WmInventoryEntity row) {
        status(row.getStatus());
        if (row.getMaterialId() == null || row.getWarehouseId() == null || row.getQuantity() == null)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public void validate(WmInventoryEntity row) {
        validateInventoryFields(row);
        lockMaterials(List.of(row.getMaterialId()));
        lockWarehouses(List.of(row.getWarehouseId()));
    }

    public void lockDemandReferences(Long material, List<Long> warehouseIds) {
        lockMaterials(List.of(material));
        lockWarehouses(warehouseIds);
    }

    public void validateBatch(List<ExternalWmInventory> rows) {
        lockMaterials(rows.stream().map(ExternalWmInventory::materialId).distinct().sorted().toList());
        lockWarehouses(rows.stream().map(ExternalWmInventory::warehouseId).distinct().sorted().toList());
    }

    private void lockMaterials(List<Long> ids) {
        if (ids.isEmpty()) return;
        var filter = new MaterialExample();
        filter.createCriteria().andIdIn(ids);
        // Existing generated same-value UPDATE acquires row locks shared with reference deletion.
        var lock = new MaterialEntity();

        for (var reference : materials.selectByExample(filter).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId())).toList()) {
            lock.setId(reference.getId());
            var rowFilter = new MaterialExample();
            rowFilter.createCriteria().andIdEqualTo(reference.getId());
            if (materials.updateByExampleSelective(lock, rowFilter) != 1) {
                throw new FoundationException(ErrorCode.NOT_FOUND);
            }
        }
        if (materials.countByExample(filter) != ids.size())
            throw new FoundationException(ErrorCode.STOCK_REFERENCE_INVALID);
    }

    private void lockWarehouses(List<Long> ids) {
        if (ids.isEmpty()) return;
        var filter = new WmWarehouseExample();
        filter.createCriteria().andIdIn(ids);
        var lock = new WmWarehouseEntity();

        for (var reference : warehouses.selectByExample(filter).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId())).toList()) {
            lock.setId(reference.getId());
            var rowFilter = new WmWarehouseExample();
            rowFilter.createCriteria().andIdEqualTo(reference.getId());
            if (warehouses.updateByExampleSelective(lock, rowFilter) != 1) {
                throw new FoundationException(ErrorCode.NOT_FOUND);
            }
        }
        if (warehouses.countByExample(filter) != ids.size())
            throw new FoundationException(ErrorCode.STOCK_REFERENCE_INVALID);
    }

    public List<WmInventoryItem> describe(List<WmInventoryEntity> rows) {
        if (rows.isEmpty()) return List.of();
        var materialFilter = new MaterialExample();
        materialFilter.createCriteria()
                .andIdIn(rows.stream().map(WmInventoryEntity::getMaterialId).distinct().toList());
        var warehouseFilter = new WmWarehouseExample();
        warehouseFilter.createCriteria()
                .andIdIn(rows.stream().map(WmInventoryEntity::getWarehouseId).distinct().toList());
        var materialNames = materials.selectByExample(materialFilter).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, row -> row.getCode() + " · " + row.getName()));
        var warehouseIndex = warehouses.selectByExample(warehouseFilter).stream()
                .collect(Collectors.toMap(WmWarehouseEntity::getId, row -> row));
        return rows.stream().map(row -> new WmInventoryItem(row,
                materialNames.get(row.getMaterialId()), warehouseIndex.containsKey(row.getWarehouseId()) ? warehouseIndex.get(row.getWarehouseId())
                .getCode() + " · " + warehouseIndex.get(row.getWarehouseId()).getName() : null,
                warehouseIndex.containsKey(row.getWarehouseId()) ? warehouseIndex.get(row.getWarehouseId())
                        .getSource() : null)).toList();
    }

    public void deletingWarehouses(List<Long> ids) {
        lockWarehouses(ids);
        var demandFilter = new MesWorkorderMaterialExample();
        for (var id : ids) {
            String v = id.toString();
            demandFilter.or().andWarehouseIdsEqualTo("[" + v + "]");
            demandFilter.or().andWarehouseIdsLike("[" + v + ",%");
            demandFilter.or().andWarehouseIdsLike("%," + v + ",%");
            demandFilter.or().andWarehouseIdsLike("%," + v + "]");
        }
        if (demand.countByExample(demandFilter) > 0) throw new FoundationException(ErrorCode.WAREHOUSE_REFERENCED);
        var filter = new WmInventoryExample();
        filter.createCriteria().andWarehouseIdIn(ids);
        if (inventories.countByExample(filter) > 0)
            throw new FoundationException(ErrorCode.WAREHOUSE_REFERENCED);
    }

    public void deletingMaterials(List<Long> ids) {
        lockMaterials(ids);
        var demandFilter = new MesWorkorderMaterialExample();
        demandFilter.createCriteria().andMaterialIdIn(ids);
        if (demand.countByExample(demandFilter) > 0)
            throw new FoundationException(ErrorCode.MATERIAL_INVENTORY_REFERENCED);
        var filter = new WmInventoryExample();
        filter.createCriteria().andMaterialIdIn(ids);
        if (inventories.countByExample(filter) > 0)
            throw new FoundationException(ErrorCode.MATERIAL_INVENTORY_REFERENCED);
    }
}
