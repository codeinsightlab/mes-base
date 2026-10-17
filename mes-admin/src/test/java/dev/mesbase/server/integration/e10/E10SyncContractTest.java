package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class E10SyncContractTest {
    private E10SyncContract.Rule rule(String conditions, String orders) {
        return new E10SyncContract.Rule("TEST ONLY", List.of("/site", "/id"), "/site", "A", null, null,
                "/status", Map.of("Y", "ENABLED"), null, List.of(), null, false, null, null, null, conditions, orders);
    }

    @Test
    void applicationJackson3AndSdkJackson2KeepTheSameConfigurationContract() throws Exception {
        var expected = new E10SyncContract(true, null, rule("[]", "[{\"field_name\":\"id\",\"order_type\":\"asc\"}]"));
        expected.validate();
        var web = JsonMapper.builder().build();
        String wire = web.writeValueAsString(expected);
        var decoded = web.readValue(wire, E10SyncContract.class);
        decoded.validate();
        assertEquals(expected, decoded);
        var stored = new ObjectMapper().readValue(wire, E10SyncContract.class);
        assertEquals(expected, stored);
        assertEquals(1, stored.warehouse().orders().size());
        assertEquals("[\"A\",\"7\"]", E10ExternalIdentity.resolve(new ObjectMapper().readTree("{\"site\":\"A\",\"id\":\"7\"}"), stored.warehouse()));
        assertNull(E10ExternalIdentity.resolve(new ObjectMapper().readTree("{\"site\":\"A\"}"), stored.warehouse()));
    }

    @Test
    void rejectsMissingOrInvalidOrderingAndOversizedConfiguration() {
        for (String orders : List.of("[]", "{}", "null", "invalid"))
            assertThrows(IllegalArgumentException.class, () -> rule("[]", orders).validate(false));
        assertThrows(IllegalArgumentException.class, () -> rule(" ".repeat(32769), "[{}]").validate(false));
    }
}
