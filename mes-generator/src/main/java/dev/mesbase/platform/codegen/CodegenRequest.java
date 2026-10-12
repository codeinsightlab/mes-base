package dev.mesbase.platform.codegen;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Developer datasource selection is independent of runtime database routing.
 */
public record CodegenRequest(
        String datasource, String schema, String tableName, String moduleName,
        String businessName, String packageName, String author, ScopeKind scope,
        OverwritePolicy overwritePolicy, boolean dryRun, Path backendRoot, Path frontendRoot,
        String backendModule, boolean generateSync, String syncMatchKey) {
    public CodegenRequest {
        if (moduleName == null || moduleName.isBlank()) {
            moduleName = defaultModule(tableName);
        }
        if (packageName == null || packageName.isBlank()) {
            packageName = "dev.mesbase." + (scope == ScopeKind.GLOBAL ? "platform." : "modules.")
                    + moduleName;
        }
    }

    public static String defaultModule(String tableName) {
        JdbcMetadataReader.identifier(tableName);
        int separator = tableName.indexOf('_');
        return separator > 0 ? tableName.substring(0, separator).toLowerCase(Locale.ROOT) : "business";
    }

    public CodegenRequest(String datasource, String schema, String tableName, String moduleName,
                          String businessName, String packageName, String author, ScopeKind scope,
                          OverwritePolicy overwritePolicy, boolean dryRun, Path backendRoot, Path frontendRoot,
                          String backendModule) {
        this(datasource, schema, tableName, moduleName, businessName, packageName, author, scope,
                overwritePolicy, dryRun, backendRoot, frontendRoot, backendModule, false, null);
    }

    public CodegenRequest(String datasource, String schema, String tableName, String moduleName,
                          String businessName, String packageName, String author, ScopeKind scope,
                          OverwritePolicy overwritePolicy, boolean dryRun, Path backendRoot, Path frontendRoot) {
        this(datasource, schema, tableName, moduleName, businessName, packageName, author, scope,
                overwritePolicy, dryRun, backendRoot, frontendRoot, "mes-business");
    }

    public Path targetBackendRoot() {
        if (backendModule == null || !backendModule.matches("[a-z][a-z0-9-]*")) {
            throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        }
        return backendRoot.resolve(backendModule);
    }

    public enum ScopeKind {GLOBAL, FACTORY_SCOPED}

    public enum OverwritePolicy {FAIL, REPLACE_GENERATED}
}
