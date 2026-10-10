package dev.mesbase.platform.codegen;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.lang.model.SourceVersion;
import javax.sql.DataSource;

import static dev.mesbase.platform.codegen.CodegenException.Code.DATASOURCE_UNAVAILABLE;
import static dev.mesbase.platform.codegen.CodegenException.Code.INVALID_REQUEST;
import static dev.mesbase.platform.codegen.CodegenException.Code.TABLE_NOT_FOUND;
import static dev.mesbase.platform.codegen.CodegenException.Code.UNSUPPORTED_SCHEMA;

/**
 * Intentionally has no Statement / PreparedStatement / data-query API.
 */
public final class JdbcMetadataReader {
    public TableMetadata read(DataSource source, String schema, String table) {
        identifier(table);
        if (schema != null) identifier(schema);
        try (var connection = source.getConnection()) {
            var meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            String found = null, actualSchema = null, comment = null;
            try (var tables = meta.getTables(catalog, schema, table, new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        if (found != null) throw new CodegenException(UNSUPPORTED_SCHEMA);
                        found = tables.getString("TABLE_NAME");
                        actualSchema = tables.getString("TABLE_SCHEM");
                        comment = tables.getString("REMARKS");
                    }
                }
            }
            // JDBC pattern matching is case-sensitive on some drivers. Retry canonical metadata case only.
            if (found == null) {
                String normalized = meta.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table.toLowerCase(Locale.ROOT);
                String ns = schema == null ? null : (meta.storesUpperCaseIdentifiers() ? schema.toUpperCase(Locale.ROOT) : schema.toLowerCase(Locale.ROOT));
                try (var tables = meta.getTables(catalog, ns, normalized, new String[]{"TABLE"})) {
                    while (tables.next()) {
                        if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                            if (found != null) throw new CodegenException(UNSUPPORTED_SCHEMA);
                            found = tables.getString("TABLE_NAME");
                            actualSchema = tables.getString("TABLE_SCHEM");
                            comment = tables.getString("REMARKS");
                        }
                    }
                }
            }
            if (found == null) throw new CodegenException(TABLE_NOT_FOUND);
            identifier(found);
            if (actualSchema != null) identifier(actualSchema);
            Set<String> pk = new HashSet<>();
            try (var keys = meta.getPrimaryKeys(catalog, actualSchema, found)) {
                while (keys.next()) pk.add(keys.getString("COLUMN_NAME"));
            }
            List<TableMetadata.Column> columns = new ArrayList<>();
            try (var rows = meta.getColumns(catalog, actualSchema, found, null)) {
                while (rows.next()) {
                    String name = rows.getString("COLUMN_NAME");
                    identifier(name);
                    int type = rows.getInt("DATA_TYPE");
                    columns.add(new TableMetadata.Column(name, camel(name), type, javaType(type), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"), rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable, pk.contains(name), "YES".equals(rows.getString("IS_AUTOINCREMENT")), safe(rows.getString("COLUMN_DEF")), safe(rows.getString("REMARKS"))));
                }
            }
            record IndexParts(boolean unique, TreeMap<Integer, String> columns) {
            }
            Map<String, IndexParts> indexes = new LinkedHashMap<>();
            try (var rows = meta.getIndexInfo(catalog, actualSchema, found, false, true)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME"), col = rows.getString("COLUMN_NAME");
                    if (name == null || col == null) continue;
                    identifier(col);
                    var parts = indexes.computeIfAbsent(name, k -> new IndexParts(!getBoolean(rows, "NON_UNIQUE"), new TreeMap<>()));
                    parts.columns().put(rows.getInt("ORDINAL_POSITION"), col);
                }
            }
            List<TableMetadata.Reference> references = new ArrayList<>();
            try (var rows = meta.getImportedKeys(catalog, actualSchema, found)) {
                while (rows.next())
                    references.add(new TableMetadata.Reference(rows.getString("FKCOLUMN_NAME"), rows.getString("PKTABLE_SCHEM"), rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME")));
            }
            return new TableMetadata(catalog, actualSchema, found, safe(comment), List.copyOf(columns), indexes.entrySet()
                    .stream()
                    .map(e -> new TableMetadata.Index(safe(e.getKey()), e.getValue().unique(), List.copyOf(e.getValue()
                            .columns().values())))
                    .toList(), List.copyOf(references), meta.getIdentifierQuoteString(), new HashSet<>(Arrays.asList(meta.getSQLKeywords()
                    .split(","))));
        } catch (SQLException e) {
            throw new CodegenException(DATASOURCE_UNAVAILABLE, e);
        }
    }

    private static boolean getBoolean(ResultSet r, String key) {
        try {
            return r.getBoolean(key);
        } catch (SQLException e) {
            throw new CodegenException(DATASOURCE_UNAVAILABLE, e);
        }
    }

    static void identifier(String s) {
        if (s == null || !s.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) throw new CodegenException(INVALID_REQUEST);
    }

    static String safe(String s) {
        if (s == null) return null;
        return s.replace("*/", "* /").replaceAll("[\\p{Cntrl}]", " ")
                .substring(0, Math.min(s.replace("*/", "* /").replaceAll("[\\p{Cntrl}]", " ").length(), 500));
    }

    public static String camel(String name) {
        StringBuilder b = new StringBuilder();
        boolean upper = false;
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            b.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        String s = b.toString();
        if (SourceVersion.isKeyword(s) || !SourceVersion.isIdentifier(s))
            throw new CodegenException(UNSUPPORTED_SCHEMA);
        return s;
    }

    // RuoYi GenUtils type categories retained, JDBC numeric precision improves legacy display-width guesses.
    static String javaType(int type) {
        return switch (type) {
            case Types.BIGINT -> "Long";
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Integer";
            case Types.DOUBLE, Types.FLOAT -> "Double";
            case Types.REAL -> "Float";
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR -> "String";
            case Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal";
            case Types.TIMESTAMP -> "java.time.LocalDateTime";
            case Types.DATE -> "java.time.LocalDate";
            case Types.BOOLEAN, Types.BIT -> "Boolean";
            default -> throw new CodegenException(UNSUPPORTED_SCHEMA);
        };
    }
}
