package dev.mesbase.business.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.mesbase.business.production.domain.ProductionQrCode;
import dev.mesbase.business.production.domain.ProductionQrCode.MaterialBatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Explicit, read-only export input; never connects to or mutates a database.
 */
class ProductionQrCodeDatasetTest {
    @Test
    void sourceInputsRemainDeterministicAcrossPermutationsAndDoNotAliasDifferentContents() throws Exception {
        String input = System.getProperty("production.qr.dataset");
        assumeTrue(input != null, "Supply -Dproduction.qr.dataset=/absolute/path/to/source-inputs.jsonl");
        var source = Path.of(input);
        var json = new ObjectMapper();
        var contentByCode = new HashMap<String, ProductionQrCode.Content>();
        var counts = new HashMap<String, Integer>();
        var random = new Random(20261103L);
        var expectedCodes = new ArrayList<String>();
        int samples = 0;
        int permutations = 0;
        int multiMaterial = 0;
        int multiBatch = 0;
        for (String line : Files.readAllLines(source)) {
            var row = json.readTree(line);
            String order = row.get("workorderCode").asText();
            var materials = new ArrayList<MaterialBatch>();
            for (var item : row.get("materials")) {
                String batch = null;
                if (!item.get("batchNo").isNull()) batch = item.get("batchNo").asText();
                materials.add(new MaterialBatch(item.get("materialCode").asText(), batch));
            }
            String code = ProductionQrCode.generate(order, materials);
            var parsed = ProductionQrCode.parse(code);
            if (System.getProperty("production.qr.codes") != null) {
                expectedCodes.add(json.writeValueAsString(java.util.Map.of(
                        "executionId", row.get("executionId").asText(), "code", code)));
            }
            assertEquals(order, parsed.workorderCode());
            assertEquals(code, ProductionQrCode.normalize(code));
            var prior = contentByCode.putIfAbsent(code, parsed);
            if (prior != null) assertEquals(prior, parsed, "Different inputs aliased to one code");
            counts.merge(code, 1, Integer::sum);
            if (materials.stream().map(MaterialBatch::materialCode).distinct().count() > 1) multiMaterial++;
            if (materials.stream().map(MaterialBatch::materialCode).distinct().count() < materials.size()) multiBatch++;
            var reordered = new ArrayList<>(materials);
            Collections.reverse(reordered);
            assertEquals(code, ProductionQrCode.generate(order, reordered));
            permutations++;
            for (int i = 0; i < 10; i++) {
                Collections.shuffle(reordered, random);
                assertEquals(code, ProductionQrCode.generate(order, reordered));
                permutations++;
            }
            reordered.addAll(materials);
            assertEquals(code, ProductionQrCode.generate(order, reordered));
            permutations++;
            samples++;
        }
        assertTrue(samples > 0);
        var report = new LinkedHashMap<String, Object>();
        report.put("evidence", "Java protocol verification against a read-only exported dataset; no business writes");
        report.put("sourceSha256", HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        report.put("samples", samples);
        report.put("permutationAndDuplicateChecks", permutations);
        report.put("uniqueCanonicalCodes", counts.size());
        report.put("sameInputGroupsWithMultipleExecutions", counts.values().stream().filter(n -> n > 1).count());
        report.put("executionsInRepeatedInputGroups", counts.values().stream().filter(n -> n > 1)
                .mapToInt(Integer::intValue).sum());
        report.put("multiMaterialSamples", multiMaterial);
        report.put("multiBatchSameMaterialSamples", multiBatch);
        report.put("differentCanonicalInputsSharingOneCode", 0);
        String output = json.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        System.out.println(output);
        String destination = System.getProperty("production.qr.report");
        if (destination != null) Files.writeString(Path.of(destination), output + System.lineSeparator());
        String codesFile = System.getProperty("production.qr.codes");
        if (codesFile != null) Files.write(Path.of(codesFile), expectedCodes);
    }
}
