package dev.mesbase.platform.codegen;
import java.util.List;
/** Only JDBC metadata, never business rows. */
public record TableMetadata(String catalog,String schema,String table,String comment,List<Column> columns,List<Index> indexes,List<Reference> references,String identifierQuote) {
 public TableMetadata {
  if(!java.util.Set.of("\"","`").contains(identifierQuote))throw new CodegenException(CodegenException.Code.UNSUPPORTED_SCHEMA);
 }
 public TableMetadata(String catalog,String schema,String table,String comment,List<Column> columns,List<Index> indexes,List<Reference> references) {
  this(catalog,schema,table,comment,columns,indexes,references,"\"");
 }
 public record Column(String name,String javaField,int jdbcType,String javaType,int size,int scale,boolean nullable,boolean primaryKey,boolean autoIncrement,String defaultValue,String comment) {}
 public record Index(String name,boolean unique,List<String> columns) {}
 public record Reference(String column,String referencedSchema,String referencedTable,String referencedColumn) {}
}
