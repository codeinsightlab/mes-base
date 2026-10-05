package dev.mesbase.platform.codegen;
import java.util.List;
public record CodegenResult(Status status,String datasource,String table,TableMetadata metadata,List<GeneratedFile> files,List<String> warnings) {
 public enum Status { DRY_RUN, GENERATED }
 public enum Target { BACKEND, FRONTEND, PERMISSION }
 public record GeneratedFile(Target target,String path,String sha256) {}
}
