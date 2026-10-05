package dev.mesbase.platform.codegen;
/** Stable safe tool failures. Causes retained in process, never dumped by CLI. */
public final class CodegenException extends RuntimeException {
 public enum Code { INVALID_REQUEST, UNKNOWN_DATASOURCE, DATASOURCE_UNAVAILABLE, TABLE_NOT_FOUND, UNSUPPORTED_SCHEMA, OUTPUT_CONFLICT, OUTPUT_FAILURE, TEMPLATE_FAILURE }
 private final Code code;
 public CodegenException(Code code){this(code,null);}
 public CodegenException(Code code,Throwable cause){super(code.name(),cause);this.code=code;}
 public Code code(){return code;}
}
