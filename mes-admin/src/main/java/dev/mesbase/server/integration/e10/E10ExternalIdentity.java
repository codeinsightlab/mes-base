package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Uses only explicitly verified locator fields. Composite identity is an unambiguous JSON tuple, never a guessed hash.
 */
public final class E10ExternalIdentity {
    private E10ExternalIdentity() {
    }

    public static String resolve(JsonNode row, E10SyncContract.Rule rule) {
        if (rule == null || rule.evidence() == null || rule.evidence().isBlank()) return null;
        var values = JsonNodeFactory.instance.arrayNode();
        for (String pointer : rule.identityPointers()) {
            String value = text(row, pointer);
            if (value == null || value.isBlank()) return null;
            values.add(value);
        }
        if (values.isEmpty()) return null;
        String identity = values.size() == 1 ? values.get(0).asText() : values.toString();
        return identity.length() > 128 ? null : identity;
    }

    public static String text(JsonNode row, String pointer) {
        if (row == null || pointer == null) return null;
        var value = row.at(pointer);
        return value.isMissingNode() || value.isNull() || !value.isValueNode() ? null : value.asText();
    }
}
