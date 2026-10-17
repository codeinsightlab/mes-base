package dev.mesbase.business.quality;

import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.core.error.FoundationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionQualityReferenceQueryTest {
    @Test
    void usesOnlyHistoryExistenceAndPropagatesDatabaseFailure() {
        var mapper = mock(MesProductionDefectMapper.class);
        var query = new ExecutionQualityReferenceQueryImpl(mapper, mock(QualityTreatmentService.class));
        when(mapper.existsExecutionHistory(1L)).thenReturn(true);
        when(mapper.existsExecutionHistory(2L)).thenReturn(false);
        when(mapper.existsExecutionHistory(3L)).thenThrow(new DataAccessResourceFailureException("TEST ONLY unavailable"));
        assertTrue(query.hasAnyHistory(1L));
        assertFalse(query.hasAnyHistory(2L));
        assertThrows(DataAccessResourceFailureException.class, () -> query.hasAnyHistory(3L));
        assertThrows(FoundationException.class, () -> query.hasAnyHistory(null));
        verify(mapper).existsExecutionHistory(1L);
        verify(mapper).existsExecutionHistory(2L);
        verify(mapper).existsExecutionHistory(3L);
        verifyNoMoreInteractions(mapper);
    }
}
