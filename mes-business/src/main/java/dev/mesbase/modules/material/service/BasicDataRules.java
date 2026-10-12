package dev.mesbase.modules.material.service;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import dev.mesbase.modules.material.dao.MaterialMapper;
import dev.mesbase.modules.material.domain.MaterialEntity;
import dev.mesbase.modules.material.domain.MaterialExample;
import dev.mesbase.modules.material.dao.MaterialCategoryMapper;
import dev.mesbase.modules.material.domain.MaterialCategoryEntity;
import dev.mesbase.modules.material.domain.MaterialCategoryExample;
import dev.mesbase.modules.material.dao.UnitMeasureMapper;
import dev.mesbase.modules.material.domain.UnitMeasureEntity;
import dev.mesbase.modules.material.domain.UnitMeasureExample;
import dev.mesbase.modules.material.dao.WorkstationMapper;
import dev.mesbase.modules.material.domain.WorkstationEntity;
import dev.mesbase.modules.material.domain.WorkstationExample;
import dev.mesbase.modules.material.dao.WorkshopMapper;
import dev.mesbase.modules.material.domain.WorkshopEntity;
import dev.mesbase.modules.material.domain.WorkshopExample;
import dev.mesbase.modules.material.dao.SafetyStockMapper;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockExample;

/**
 * Shared minimal business checks for manual CRUD and synchronization.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class BasicDataRules {
    private final MaterialMapper material;
    private final MaterialCategoryMapper materialCategory;
    private final UnitMeasureMapper unitMeasure;
    private final WorkstationMapper workstation;
    private final WorkshopMapper workshop;
    private final SafetyStockMapper safetyStock;

    public BasicDataRules(MaterialMapper material, MaterialCategoryMapper materialCategory,
                          UnitMeasureMapper unitMeasure, WorkstationMapper workstation, WorkshopMapper workshop,
                          SafetyStockMapper safetyStock) {
        this.material = material;
        this.materialCategory = materialCategory;
        this.unitMeasure = unitMeasure;
        this.workstation = workstation;
        this.workshop = workshop;
        this.safetyStock = safetyStock;
    }

    private String factory() {
        return FactoryContext.required().factoryId().value();
    }

    private void reject(boolean condition) {
        if (condition) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private void status(String value) {
        if (!Set.of("ENABLED", "DISABLED").contains(value)) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public void validate(MaterialEntity row) {
        status(row.getStatus());
        var example = new MaterialExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        reject(material.countByExample(example) > 0);
        requireUnit(row.getUnitId());
        if (row.getCategoryId() != null) requireCategory(row.getCategoryId());
        if (!Set.of("ITEM", "PRODUCT").contains(row.getMaterialType()))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public void validate(MaterialCategoryEntity row) {
        status(row.getStatus());
        var example = new MaterialCategoryExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        reject(materialCategory.countByExample(example) > 0);
        if (row.getSort() == null || row.getSort() < 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
        Long parent = row.getParentId();
        Set<Long> seen = new HashSet<>();
        if (row.getId() != null) seen.add(row.getId());
        while (parent != null) {
            reject(!seen.add(parent));
            var ancestor = requireCategory(parent);
            parent = ancestor.getParentId();
        }
    }

    public void validate(UnitMeasureEntity row) {
        status(row.getStatus());
        var example = new UnitMeasureExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        reject(unitMeasure.countByExample(example) > 0);
    }

    public void validate(WorkstationEntity row) {
        status(row.getStatus());
        var example = new WorkstationExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        reject(workstation.countByExample(example) > 0);
        if (row.getWorkshopId() != null) requireWorkshop(row.getWorkshopId());
    }

    public void validate(WorkshopEntity row) {
        status(row.getStatus());
        var example = new WorkshopExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        reject(workshop.countByExample(example) > 0);
    }

    public void validate(SafetyStockEntity row) {
        status(row.getStatus());
        var example = new SafetyStockExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(factory())
                .andMaterialIdEqualTo(row.getMaterialId());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        reject(safetyStock.countByExample(example) > 0);
        requireMaterial(row.getMaterialId());
        if (row.getMinQty() == null || row.getMinQty().signum() < 0 || row.getMaxQty() != null && row.getMaxQty()
                .compareTo(row.getMinQty()) < 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public MaterialEntity requireMaterial(Long id) {
        var row = id == null ? null : material.selectByPrimaryKey(id);
        if (row == null || !factory().equals(row.getFactoryId())) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    public MaterialCategoryEntity requireCategory(Long id) {
        var row = id == null ? null : materialCategory.selectByPrimaryKey(id);
        if (row == null || !factory().equals(row.getFactoryId())) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    public UnitMeasureEntity requireUnit(Long id) {
        var row = id == null ? null : unitMeasure.selectByPrimaryKey(id);
        if (row == null || !factory().equals(row.getFactoryId())) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    public WorkshopEntity requireWorkshop(Long id) {
        var row = id == null ? null : workshop.selectByPrimaryKey(id);
        if (row == null || !factory().equals(row.getFactoryId())) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    public void deletingMaterial(List<Long> ids) {
        var safetyStockRefs = new SafetyStockExample();
        safetyStockRefs.createCriteria().andFactoryIdEqualTo(factory()).andMaterialIdIn(ids);
        reject(safetyStock.countByExample(safetyStockRefs) > 0);
    }

    public void deletingMaterialCategory(List<Long> ids) {
        var materialRefs = new MaterialExample();
        materialRefs.createCriteria().andFactoryIdEqualTo(factory()).andCategoryIdIn(ids);
        reject(material.countByExample(materialRefs) > 0);
        var materialCategoryRefs = new MaterialCategoryExample();
        materialCategoryRefs.createCriteria().andFactoryIdEqualTo(factory()).andParentIdIn(ids);
        reject(materialCategory.countByExample(materialCategoryRefs) > 0);
    }

    public void deletingUnitMeasure(List<Long> ids) {
        var materialRefs = new MaterialExample();
        materialRefs.createCriteria().andFactoryIdEqualTo(factory()).andUnitIdIn(ids);
        reject(material.countByExample(materialRefs) > 0);
    }

    public void deletingWorkstation(List<Long> ids) {
    }

    public void deletingWorkshop(List<Long> ids) {
        var workstationRefs = new WorkstationExample();
        workstationRefs.createCriteria().andFactoryIdEqualTo(factory()).andWorkshopIdIn(ids);
        reject(workstation.countByExample(workstationRefs) > 0);
    }

    public void deletingSafetyStock(List<Long> ids) {
    }

    public Long unitId(String code) {
        var example = new UnitMeasureExample();
        example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(code);
        var rows = unitMeasure.selectByExample(example);
        if (rows.size() != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
        return rows.getFirst().getId();
    }

    public Long categoryId(String code) {
        var example = new MaterialCategoryExample();
        example.createCriteria().andFactoryIdEqualTo(factory()).andCodeEqualTo(code);
        var rows = materialCategory.selectByExample(example);
        if (rows.size() != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
        return rows.getFirst().getId();
    }
}
