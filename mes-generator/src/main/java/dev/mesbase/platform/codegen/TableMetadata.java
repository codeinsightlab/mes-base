package dev.mesbase.platform.codegen;

import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.HashSet;

/**
 * Only JDBC metadata, never business rows.
 */
public record TableMetadata(String catalog, String schema, String table, String comment, List<Column> columns,
                            List<Index> indexes, List<Reference> references, String identifierQuote,
                            Set<String> sqlKeywords) {
    private static final Set<String> STANDARD_SQL_KEYWORDS = Set.of(
            "ALL", "AND", "ANY", "AS", "ASC", "BETWEEN", "BY", "CASE", "CHECK", "COLUMN", "CONSTRAINT", "CREATE",
            "CROSS", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "DEFAULT", "DELETE", "DESC", "DISTINCT",
            "DROP", "ELSE", "END", "EXCEPT", "EXISTS", "FALSE", "FETCH", "FOR", "FOREIGN", "FROM", "FULL", "GROUP",
            "HAVING", "IN", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE", "LIMIT",
            "NOT", "NULL", "OFFSET", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "REFERENCES", "RIGHT", "SELECT",
            "SET", "TABLE", "THEN", "TRUE", "UNION", "UNIQUE", "UPDATE", "USER", "USING", "VALUES", "WHEN", "WHERE", "WITH");

    public TableMetadata {
        Set<String> keywords = new HashSet<>(STANDARD_SQL_KEYWORDS);
        sqlKeywords.forEach(keyword -> keywords.add(keyword.toUpperCase(Locale.ROOT)));
        sqlKeywords = Set.copyOf(keywords);
        if (!Set.of("\"", "`").contains(identifierQuote))
            throw new CodegenException(CodegenException.Code.UNSUPPORTED_SCHEMA);
    }

    public boolean requiresDelimiter(String identifier) {
        JdbcMetadataReader.identifier(identifier);
        return sqlKeywords.contains(identifier.toUpperCase(Locale.ROOT));
    }

    public String sqlIdentifier(String identifier) {
        return requiresDelimiter(identifier) ? identifierQuote + identifier + identifierQuote : identifier;
    }

    public TableMetadata(String catalog, String schema, String table, String comment, List<Column> columns,
                         List<Index> indexes, List<Reference> references, String identifierQuote) {
        this(catalog, schema, table, comment, columns, indexes, references, identifierQuote, Set.of());
    }

    public TableMetadata(String catalog, String schema, String table, String comment, List<Column> columns,
                         List<Index> indexes, List<Reference> references) {
        this(catalog, schema, table, comment, columns, indexes, references, "\"");
    }

    public record Column(String name, String javaField, int jdbcType, String javaType, int size, int scale,
                         boolean nullable, boolean primaryKey, boolean autoIncrement, boolean generated,
                         String defaultValue, String comment) {
        public Column(String name, String javaField, int jdbcType, String javaType, int size, int scale,
                      boolean nullable, boolean primaryKey, boolean autoIncrement, String defaultValue,
                      String comment) {
            this(name, javaField, jdbcType, javaType, size, scale, nullable, primaryKey, autoIncrement, false,
                    defaultValue, comment);
        }
    }

    public record Index(String name, boolean unique, List<String> columns) {
    }

    public record Reference(String column, String referencedSchema, String referencedTable, String referencedColumn) {
    }
}
