package dev.mesbase.business.basic;

import dev.mesbase.business.basic.dao.EquipmentMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.basic.domain.WorkstationEntity;
import dev.mesbase.business.basic.service.EquipmentReferenceRules;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentReferencesTest {
    @Test
    void foreignOrMissingWorkstationFailsBeforeCheckingEquipmentAndReferencedDeleteFails() {
        var stations = mock(WorkstationMapper.class);
        var equipment = mock(EquipmentMapper.class);
        var rules = new EquipmentReferenceRules(stations, equipment);
        FactoryContext.bind(new FactoryScope(new FactoryId("A")));
        try {
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(FoundationException.class,
                    () -> rules.deletingWorkstations(List.of(1L))).code());
            verify(equipment, never()).countByExample(any());
            var station = new WorkstationEntity();
            station.setId(1L);
            when(stations.selectByExample(any())).thenReturn(List.of(station));
            when(stations.selectByPrimaryKeyForUpdate(1L)).thenReturn(station);
            when(equipment.countByExample(any())).thenReturn(1L);
            assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(FoundationException.class,
                    () -> rules.deletingWorkstations(List.of(1L))).code());
        } finally {
            FactoryContext.clear();
        }
    }
}
