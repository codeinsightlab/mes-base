package dev.mesbase.business.shared;

import dev.mesbase.business.basic.domain.MaterialType;
import dev.mesbase.business.directory.domain.DirectoryDeletionFlag;
import dev.mesbase.business.directory.domain.DirectoryStatus;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.business.integration.warehouse.ExternalEffect;
import dev.mesbase.business.issue.domain.MaterialIssueOperation;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.RoutingStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BusinessEnumContractTest {
    private static final List<Class<?>> TYPES = List.of(
            MaterialType.class,
            DirectoryDeletionFlag.class,
            DirectoryStatus.class,
            Status.class,
            Mode.class,
            Outcome.class,
            DocumentStatus.class,
            ExternalEffect.class,
            MaterialIssueOperation.class,
            MaterialIssueStatus.class,
            MaterialIssueSubmissionStatus.class,
            ExecutionPathStatus.class,
            ProductionExecutionStatus.class,
            RoutingStatus.class,
            WorkOrderStatus.class,
            EnableStatus.class);

    @Test
    void valuesRoundTripAndOptionsFollowDeclarationOrder() throws Exception {
        for (Class<?> type : TYPES) {
            var from = type.getMethod("from", String.class);
            var value = type.getMethod("getValue");
            var description = type.getMethod("getDescription");
            Map<String, String> expected = new LinkedHashMap<>();
            for (Object item : type.getEnumConstants()) {
                String code = (String) value.invoke(item);
                String label = (String) description.invoke(item);
                assertFalse(label.isBlank(), type.getName());
                assertNull(expected.put(code, label), "重复枚举值：" + type.getName());
                assertSame(item, from.invoke(null, code));
            }
            assertNull(from.invoke(null, new Object[]{null}));
            assertNull(from.invoke(null, "__INVALID_ENUM_VALUE__"));
            Map<?, ?> actual = (Map<?, ?>) type.getMethod("toMap").invoke(null);
            assertEquals(new ArrayList<>(expected.entrySet()), new ArrayList<>(actual.entrySet()));
            actual.clear();
            assertEquals(expected, type.getMethod("toMap").invoke(null), "调用方不能修改枚举后续选项");
        }
    }

    @Test
    void persistedCodesRemainCompatible() {
        assertEquals("ENABLED", EnableStatus.ENABLED.getValue());
        assertEquals("DRAFT", WorkOrderStatus.DRAFT.getValue());
        assertEquals("CONFIRMED", MaterialIssueStatus.CONFIRMED.getValue());
        assertEquals("SUCCEEDED", MaterialIssueSubmissionStatus.SUCCEEDED.getValue());
        assertEquals("LOCAL_ONLY", Mode.LOCAL_ONLY.getValue());
        assertEquals("0", DirectoryStatus.NORMAL.getValue());
        assertEquals("2", DirectoryDeletionFlag.DELETED.getValue());
    }

    @Test
    void productionJavaAndMapperSqlDoNotRepeatBusinessEnumLiterals() throws Exception {
        Set<String> codes = new HashSet<>();
        for (Class<?> type : TYPES) {
            for (Object item : type.getEnumConstants()) {
                String value = (String) type.getMethod("getValue").invoke(item);
                // Numeric directory flags are reviewed separately: numeric HTTP defaults are not statuses.
                if (value.length() > 1) codes.add(value);
            }
        }
        var literal = Pattern.compile("[\"'](" + String.join("|", codes) + ")[\"']");
        for (String directory : List.of("src/main/java/dev/mesbase/business", "src/main/resources/dev/mesbase/business")) {
            try (var files = Files.walk(Path.of(directory))) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    if (!file.toString().endsWith(".java") && !file.toString().endsWith(".xml")) continue;
                    String source = Files.readString(file);
                    if (source.contains("public enum ")) continue;
                    assertFalse(literal.matcher(source).find(), "业务枚举硬编码：" + file);
                }
            }
        }
    }
}
