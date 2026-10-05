package dev.mesbase.platform.codegen;
import java.nio.file.Path;
/** Developer datasource selection is independent of runtime Factory identity. */
public record CodegenRequest(String datasource,String schema,String tableName,String moduleName,String businessName,String packageName,String author,ScopeKind scope,OverwritePolicy overwritePolicy,boolean dryRun,Path backendRoot,Path frontendRoot) {
 public enum ScopeKind { GLOBAL, FACTORY_SCOPED }
 public enum OverwritePolicy { FAIL, REPLACE_GENERATED }
}
