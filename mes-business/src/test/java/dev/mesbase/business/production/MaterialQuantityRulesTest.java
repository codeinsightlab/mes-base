package dev.mesbase.business.production;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;

import java.util.List;

import static org.mockito.Mockito.when;

class MaterialQuantityRulesTest {
    private final MaterialMapper materials = mock(MaterialMapper.class);
    private final UnitMeasureMapper units = mock(UnitMeasureMapper.class);
    private final MaterialQuantityRules rules = new MaterialQuantityRules(materials, units,
            mock(MesProductionExecutionMapper.class));

    private UnitMeasureEntity unit(int whole, int precision) {
        var row = new UnitMeasureEntity();
        row.setId(7L);
        row.setMustBeWholeNumber(whole);
        row.setQuantityPrecision(precision);
        return row;
    }

    private void materialUses(UnitMeasureEntity unit) {
        var material = new MaterialEntity();
        material.setId(3L);
        material.setUnitId(7L);
        when(materials.selectByPrimaryKey(3L)).thenReturn(material);
        when(units.selectByPrimaryKey(7L)).thenReturn(unit);
    }

    @Test
    void appliesUnitPrecisionWithoutRoundingAndAllowsOnlyInsignificantTrailingZeros() {
        materialUses(unit(0, 3));
        assertDoesNotThrow(() -> rules.checkForMaterial(new BigDecimal("12.345"), 3L));
        assertDoesNotThrow(() -> rules.checkForMaterial(new BigDecimal("12.345000"), 3L));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(FoundationException.class,
                () -> rules.checkForMaterial(new BigDecimal("12.345001"), 3L)).code());
    }

    @Test
    void wholeUnitsRejectFractionsButAllowZeroFractionAndSixDecimalInputShape() {
        materialUses(unit(1, 6));
        assertDoesNotThrow(() -> rules.checkForMaterial(new BigDecimal("12.000000"), 3L));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(FoundationException.class,
                () -> rules.checkForMaterial(new BigDecimal("12.000001"), 3L)).code());
    }

    @Test
    void missingMaterialOrUnitConfigurationFailsClosed() {
        assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(FoundationException.class,
                () -> rules.checkForMaterial(BigDecimal.ONE, 3L)).code());
        materialUses(null);
        assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(FoundationException.class,
                () -> rules.checkForMaterial(BigDecimal.ONE, 3L)).code());
    }

    @Test
    void tighteningAllowsOnlyExactTrustedSettlementWithoutRounding() {
        var unit = unit(1, 0);
        var material = new MaterialEntity();
        material.setId(3L);
        material.setUnitId(7L);
        when(materials.selectByPrimaryKeyForUpdate(3L)).thenReturn(material);
        when(units.selectByPrimaryKeyForUpdate(7L)).thenReturn(unit);
        var remaining = new BigDecimal("1.234567");
        assertDoesNotThrow(() -> rules.checkSettlementForWrite(remaining, remaining, 3L));
        assertThrows(FoundationException.class, () -> rules.checkSettlementForWrite(new BigDecimal("0.234567"), remaining, 3L));
        assertThrows(FoundationException.class, () -> rules.checkSettlementForWrite(new BigDecimal("1.234568"), remaining, 3L));
        assertDoesNotThrow(() -> rules.checkSettlementForWrite(BigDecimal.ONE, remaining, 3L));
        unit.setQuantityPrecision(9);
        assertThrows(FoundationException.class, () -> rules.checkSettlementForWrite(remaining, remaining, 3L));
    }

    @Test
    void locksTheCompleteMaterialSetBeforeAnyUnitAndDeduplicatesSharedUnits() {
        var first = new MaterialEntity();
        first.setId(2L);
        first.setUnitId(9L);
        var second = new MaterialEntity();
        second.setId(3L);
        second.setUnitId(7L);
        var third = new MaterialEntity();
        third.setId(4L);
        third.setUnitId(7L);
        when(materials.selectByPrimaryKeyForUpdate(2L)).thenReturn(first);
        when(materials.selectByPrimaryKeyForUpdate(3L)).thenReturn(second);
        when(materials.selectByPrimaryKeyForUpdate(4L)).thenReturn(third);
        when(units.selectByPrimaryKeyForUpdate(7L)).thenReturn(unit(0, 6));
        when(units.selectByPrimaryKeyForUpdate(9L)).thenReturn(unit(0, 6));
        rules.lockForWrite(List.of(4L, 3L, 2L, 3L));
        var order = inOrder(materials, units);
        order.verify(materials).selectByPrimaryKeyForUpdate(2L);
        order.verify(materials).selectByPrimaryKeyForUpdate(3L);
        order.verify(materials).selectByPrimaryKeyForUpdate(4L);
        order.verify(units).selectByPrimaryKeyForUpdate(7L);
        order.verify(units).selectByPrimaryKeyForUpdate(9L);
        order.verifyNoMoreInteractions();
    }
}
