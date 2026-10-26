package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Collection;
import java.util.ArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Applies a material's trusted base-unit quantity rule to a new user-entered value.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialQuantityRules {
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final MesProductionExecutionMapper executions;

    public MaterialQuantityRules(MaterialMapper materials, UnitMeasureMapper units,
                                 MesProductionExecutionMapper executions) {
        this.materials = materials;
        this.units = units;
        this.executions = executions;
    }

    public Long productIdForExecution(Long id) {
        if (id == null) return null;
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) return null;
        return execution.getProductId();
    }

    /**
     * All materials, then all units, then downstream warehouse/stock rows.
     */
    public void lockForWrite(Collection<Long> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) conflict();
        var rows = new ArrayList<MaterialEntity>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            var row = materials.selectByPrimaryKeyForUpdate(id);
            if (row == null || row.getUnitId() == null) conflict();
            rows.add(row);
        }
        for (Long id : rows.stream().map(MaterialEntity::getUnitId).distinct().sorted().toList()) {
            if (units.selectByPrimaryKeyForUpdate(id) == null) conflict();
        }
    }

    /**
     * Exact exhaustion of a trusted existing fact remains possible after tightening rules.
     */
    public void checkSettlementForWrite(BigDecimal value, BigDecimal remaining, Long materialId) {
        ProductionQuantityInput.check(value);
        var material = materials.selectByPrimaryKeyForUpdate(materialId);
        if (material == null || material.getUnitId() == null) conflict();
        var unit = units.selectByPrimaryKeyForUpdate(material.getUnitId());
        validateConfiguration(material, unit);
        if (remaining != null && remaining.signum() > 0 && value.compareTo(remaining) == 0) return;
        check(value, material, unit);
    }

    public void checkForMaterial(BigDecimal value, Long materialId) {
        checkForMaterial(value, materialId, false);
    }

    public void checkForWrite(BigDecimal value, Long materialId) {
        checkForMaterial(value, materialId, true);
    }

    public void checkForExecutionWrite(BigDecimal value, Long executionId) {
        if (executionId == null) conflict();
        MesProductionExecutionEntity execution = executions.selectByPrimaryKeyForUpdate(executionId);
        if (execution == null || execution.getProductId() == null) conflict();
        checkForWrite(value, execution.getProductId());
    }

    private void checkForMaterial(BigDecimal value, Long materialId, boolean lock) {
        ProductionQuantityInput.check(value);
        if (materialId == null) conflict();
        MaterialEntity material = lock ? materials.selectByPrimaryKeyForUpdate(materialId)
                : materials.selectByPrimaryKey(materialId);
        if (material == null || material.getUnitId() == null) conflict();
        UnitMeasureEntity unit = lock ? units.selectByPrimaryKeyForUpdate(material.getUnitId())
                : units.selectByPrimaryKey(material.getUnitId());
        check(value, material, unit);
    }

    public void changedForExecutionWrite(BigDecimal value, BigDecimal previous, Long executionId) {
        if (value == null || previous == null || value.compareTo(previous) != 0)
            checkForExecutionWrite(value, executionId);
    }

    private static void validateConfiguration(MaterialEntity material, UnitMeasureEntity unit) {
        if (unit == null || !Objects.equals(material.getUnitId(), unit.getId())
                || unit.getMustBeWholeNumber() == null || unit.getMustBeWholeNumber() < 0
                || unit.getMustBeWholeNumber() > 1 || unit.getQuantityPrecision() == null
                || unit.getQuantityPrecision() < 0 || unit.getQuantityPrecision() > 6)
            conflict();
    }

    private static void check(BigDecimal value, MaterialEntity material, UnitMeasureEntity unit) {
        validateConfiguration(material, unit);
        int significantScale = Math.max(0, value.stripTrailingZeros().scale());
        if (unit.getMustBeWholeNumber() == 1 && significantScale != 0
                || significantScale > unit.getQuantityPrecision())
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static void conflict() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
