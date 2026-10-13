package dev.mesbase.business.wm.service;

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
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import java.util.stream.Collectors;
import dev.mesbase.business.wm.domain.WmInventoryItem;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * EXTRA_CODE_REASON: current CRUD generator has no cross-table reference/deletion rules.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class StockReferenceRules {
    private final MaterialMapper materials;
    private final WmWarehouseMapper warehouses;
    private final WmInventoryMapper inventories;

    public StockReferenceRules(MaterialMapper materials, WmWarehouseMapper warehouses, WmInventoryMapper inventories) {
        this.materials = materials;
        this.warehouses = warehouses;
        this.inventories = inventories;
    }

    private String factory() {
        return FactoryContext.required().factoryId().value();
    }

    private void status(String status) {
        if (status == null || !Set.of("ENABLED", "DISABLED").contains(status))
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

    public void validateBatch(List<ExternalWmInventory> rows) {
        lockMaterials(rows.stream().map(ExternalWmInventory::materialId).distinct().sorted().toList());
        lockWarehouses(rows.stream().map(ExternalWmInventory::warehouseId).distinct().sorted().toList());
    }

    private void lockMaterials(List<Long> ids) {
        if (ids.isEmpty()) return;
        var filter = new MaterialExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andIdIn(ids);
        // Existing generated same-value UPDATE acquires row locks shared with reference deletion.
        var lock = new MaterialEntity();
        lock.setFactoryId(factory());
        materials.updateByExampleSelective(lock, filter);
        if (materials.countByExample(filter) != ids.size())
            throw new FoundationException(ErrorCode.STOCK_REFERENCE_INVALID);
    }

    private void lockWarehouses(List<Long> ids) {
        if (ids.isEmpty()) return;
        var filter = new WmWarehouseExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andIdIn(ids);
        var lock = new WmWarehouseEntity();
        lock.setFactoryId(factory());
        warehouses.updateByExampleSelective(lock, filter);
        if (warehouses.countByExample(filter) != ids.size())
            throw new FoundationException(ErrorCode.STOCK_REFERENCE_INVALID);
    }

    public List<WmInventoryItem> describe(List<WmInventoryEntity> rows) {
        if (rows.isEmpty()) return List.of();
        var materialFilter = new MaterialExample();
        materialFilter.createCriteria().andFactoryIdEqualTo(factory())
                .andIdIn(rows.stream().map(WmInventoryEntity::getMaterialId).distinct().toList());
        var warehouseFilter = new WmWarehouseExample();
        warehouseFilter.createCriteria().andFactoryIdEqualTo(factory())
                .andIdIn(rows.stream().map(WmInventoryEntity::getWarehouseId).distinct().toList());
        var materialNames = materials.selectByExample(materialFilter).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, row -> row.getCode() + " · " + row.getName()));
        var warehouseNames = warehouses.selectByExample(warehouseFilter).stream()
                .collect(Collectors.toMap(WmWarehouseEntity::getId, row -> row.getCode() + " · " + row.getName()));
        return rows.stream().map(row -> new WmInventoryItem(row,
                materialNames.get(row.getMaterialId()), warehouseNames.get(row.getWarehouseId()))).toList();
    }

    public void deletingWarehouses(List<Long> ids) {
        lockWarehouses(ids);
        var filter = new WmInventoryExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andWarehouseIdIn(ids);
        if (inventories.countByExample(filter) > 0)
            throw new FoundationException(ErrorCode.WAREHOUSE_REFERENCED);
    }

    public void deletingMaterials(List<Long> ids) {
        lockMaterials(ids);
        var filter = new WmInventoryExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andMaterialIdIn(ids);
        if (inventories.countByExample(filter) > 0)
            throw new FoundationException(ErrorCode.MATERIAL_INVENTORY_REFERENCED);
    }
}
