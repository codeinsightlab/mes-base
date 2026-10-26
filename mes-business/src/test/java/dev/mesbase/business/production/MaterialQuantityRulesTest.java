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
}
