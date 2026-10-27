package dev.mesbase.platform.codegen;

import java.util.Set;

/**
 * A caller-selected physical column, never an inferred business field name.
 */
public final class SyncGeneration {
    private SyncGeneration() {
    }

    public static TableMetadata.Column validate(TableMetadata table, String matchKey) {
        var key = table.columns().stream().filter(column -> column.name().equalsIgnoreCase(matchKey)
                        || "primary-key".equals(matchKey) && column.primaryKey())
                .findFirst().orElseThrow(() -> new CodegenException(CodegenException.Code.INVALID_SYNC_KEY));
        if (key.generated() || !Set.of("Long", "Integer", "String").contains(key.javaType())) {
            throw new CodegenException(CodegenException.Code.INVALID_SYNC_KEY);
        }
        return key;
    }
}
