package dev.mesbase.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User-designated database SELECT audit only. No migrations, grants, records or ERP calls.
 */
@EnabledIfEnvironmentVariable(named = "MES_CONFIGURATION_READONLY_ACCEPTANCE", matches = "true")
class ConfigurationConsolidationReadOnlyTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void configuredSimpleBBindingAndCancelledOrdersRemainUnchanged() throws Exception {
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> platform = (Map<?, ?>) ((Map<?, ?>) root.get("mes")).get("platform");
        var snapshot = new LinkedHashMap<String, Object>();
        try (var center = DriverManager.getConnection((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password"))) {
            center.setReadOnly(true);
            snapshot.put("factory", fingerprint(center, "mf_factory"));
        }
        try (var connection = AcceptanceBusinessDatabase.source().getConnection()) {
            connection.setReadOnly(true);
            assertEquals("mes_base_dev_business_simple_b", connection.getCatalog());
            var matcher = Pattern.compile("(?m)^\\|(\\d+)\\|[0-9a-f-]{36}\\|CANCELLED / 200\\|")
                    .matcher(Files.readString(Path.of("docs/reviews/2026-10-17-workorder-cancellation-v18-report.md")));
            var ids = new ArrayList<Long>();
            while (matcher.find()) ids.add(Long.valueOf(matcher.group(1)));
            assertEquals(176, ids.size());
            String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
            try (var query = connection.prepareStatement("SELECT COUNT(*) FROM workorder WHERE id IN (" + placeholders + ") AND status='CANCELLED'")) {
                for (int i = 0; i < ids.size(); i++) query.setLong(i + 1, ids.get(i));
                try (var rows = query.executeQuery()) {
                    rows.next();
                    assertEquals(176, rows.getInt(1));
                }
            }
            var tables = new ArrayList<String>();
            try (var query = connection.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name"); var rows = query.executeQuery()) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            var fingerprints = new LinkedHashMap<String, Object>();
            for (String table : tables) fingerprints.put(table, fingerprint(connection, table));
            snapshot.put("businessTables", fingerprints);
            snapshot.put("cancelledTargetOrders", ids.size());
        }
        Path folder = Path.of(".local/config-consolidation-20261017");
        Files.createDirectories(folder);
        String phase = System.getenv().getOrDefault("MES_CONFIG_AUDIT_PHASE", "after");
        assertTrue(List.of("before", "after").contains(phase));
        var actual = JSON.valueToTree(snapshot);
        if ("after".equals(phase)) assertEquals(JSON.readTree(folder.resolve("database-before.json").toFile()), actual);
        Files.writeString(folder.resolve("database-" + phase + ".json"), JSON.writeValueAsString(snapshot));
    }

    private static Map<String, Object> fingerprint(Connection connection, String table) throws Exception {
        assertTrue(table.matches("[A-Za-z0-9_]+"));
        var values = new ArrayList<String>();
        try (var query = connection.prepareStatement("SELECT * FROM `" + table + "`"); var rows = query.executeQuery()) {
            var metadata = rows.getMetaData();
            while (rows.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int column = 1; column <= metadata.getColumnCount(); column++)
                    row.put(metadata.getColumnName(column), rows.getString(column));
                values.add(JSON.writeValueAsString(row));
            }
        }
        values.sort(String::compareTo);
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", values).getBytes(StandardCharsets.UTF_8));
        return Map.of("count", values.size(), "sha256", HexFormat.of().formatHex(hash));
    }
}
