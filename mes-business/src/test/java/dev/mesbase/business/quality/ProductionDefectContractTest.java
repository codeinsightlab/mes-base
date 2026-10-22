package dev.mesbase.business.quality;

import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.WasteCategory;
import dev.mesbase.business.quality.domain.DefectDestination;
import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.integration.LocalProductionDefectErpGateway;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductionDefectContractTest {
    private static Command command(String quantity, String reason, Long execution) {
        return new Command("key", null, null, new Facts(execution, 13L, 13L, null, new BigDecimal(quantity), "PROCESS", reason, null, null, "SAME_NODE", null, null, null, LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    @Test
    void fingerprintIsCanonicalAndSeparatesActorCommandAndExecution() {
        var a = command("2.000000", " 缺陷 ", 1L);
        var b = command("2", "缺陷", 1L);
        var fp = ProductionDefectService.fingerprint("CREATE", null, a, 1L);
        assertEquals(fp, ProductionDefectService.fingerprint("CREATE", null, b, 1L));
        assertNotEquals(fp, ProductionDefectService.fingerprint("REVISE", "id", b, 1L));
        assertNotEquals(fp, ProductionDefectService.fingerprint("CREATE", null, b, 2L));
        assertNotEquals(fp, ProductionDefectService.fingerprint("CREATE", null, command("2", "缺陷", 2L), 1L));
        assertNotEquals(fp, ProductionDefectService.fingerprint("CREATE", null, command("2", "缺陷2", 1L), 1L));
    }

    @Test
    void mapperHasNoUpdateDeleteAndLocalGatewayHasNoExternalReference() {
        assertFalse(Arrays.stream(MesProductionDefectMapper.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("update") || m.getName().startsWith("delete")));
        var result = new LocalProductionDefectErpGateway().acknowledge(null);
        assertEquals(Mode.LOCAL_ONLY, result.executionMode());
        assertNull(result.externalReference());
    }

    @Test
    void domainEnumsPreserveFrozenCodesAndRejectUnknownValues() {
        for (var action : DefectRecordAction.values()) assertEquals(action, DefectRecordAction.from(action.getValue()));
        for (var category : WasteCategory.values()) assertEquals(category, WasteCategory.from(category.getValue()));
        for (var destination : DefectDestination.values())
            assertEquals(destination, DefectDestination.from(destination.getValue()));
        assertNull(DefectRecordAction.from(null));
        assertNull(WasteCategory.from("invalid"));
        assertNull(DefectDestination.from("invalid"));
        assertEquals("工废", WasteCategory.toMap().get("PROCESS"));
    }
}
