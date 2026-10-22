package dev.mesbase.business.quality;

import dev.mesbase.business.quality.domain.ProductionDefectModels.Processing;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentQualityProcessingTest {
    @ParameterizedTest
    @CsvSource({"CREATE,UNVERIFIED,0,0,UNVERIFIED", "CREATE,OVERLAP,0,0,OVERLAP",
            "CREATE,INDEPENDENT,0,0,PENDING", "CREATE,INDEPENDENT,4,0,PENDING",
            "CREATE,INDEPENDENT,10,0,RETURNED", "CREATE,INDEPENDENT,0,10,TRANSFERRED",
            "CREATE,INDEPENDENT,4,6,DISPOSED", "VOID,UNVERIFIED,0,0,VOIDED"})
    void statusDependsOnlyOnTheCurrentDispositionBalance(String action, String attribution, String returned,
                                                         String transferred, String expected) {
        var result = Processing.of(action, Attribution.valueOf(attribution), BigDecimal.TEN,
                new BigDecimal(returned), new BigDecimal(transferred));
        assertEquals(State.from(expected), result.state());
        assertEquals(0, BigDecimal.TEN.subtract(result.returnedQty()).subtract(result.transferredQty())
                .compareTo(result.pendingDispositionQty()));
    }

    @Test
    void rejectsOverdrawnUnverifiedAndRetiredStates() {
        assertThrows(FoundationException.class, () -> Processing.of("CREATE", Attribution.INDEPENDENT,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(FoundationException.class, () -> Processing.of("CREATE", Attribution.UNVERIFIED,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(FoundationException.class, () -> Action.from("ALLOCATE"));
        assertThrows(FoundationException.class, () -> State.from("WAITING_REWORK"));
    }

    @Test
    void queryProjectionKeepsUnknownBalancesDistinctFromZero() {
        var valid = Processing.read("CREATE", Attribution.INDEPENDENT, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        assertEquals(State.PENDING, valid.state());
        var unresolved = Processing.read("CREATE", Attribution.INDEPENDENT, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1);
        assertEquals(State.UNRESOLVED, unresolved.state());
        assertEquals(null, unresolved.pendingDispositionQty());
        assertEquals(null, unresolved.returnedQty());
        assertEquals(State.UNRESOLVED, Processing.read("CREATE", Attribution.INDEPENDENT, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, 0)
                .state());
    }

}
