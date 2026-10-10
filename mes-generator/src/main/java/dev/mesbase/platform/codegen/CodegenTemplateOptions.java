package dev.mesbase.platform.codegen;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Explicit template metadata supplied by the caller, without project defaults.
 */
public record CodegenTemplateOptions(Path templateRoot, Map<String, Object> variables,
                                     Map<String, String> packages, List<String> auditFields,
                                     int stringLengthLimit, String frontendApi, String frontendPage,
                                     String frontendMenu, String menuSql) {
    public CodegenTemplateOptions {
        variables = Map.copyOf(variables);
        packages = Map.copyOf(packages);
        auditFields = List.copyOf(auditFields);
        if (templateRoot == null || stringLengthLimit < 1) {
            throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        }
    }
}
