// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.domain.MaterialCategoryResource;

public final class MaterialCategoryPermissions {
    public static final String LIST = MaterialCategoryResource.PREFIX + ":list";
    public static final String QUERY = MaterialCategoryResource.PREFIX + ":query";
    public static final String CREATE = MaterialCategoryResource.PREFIX + ":create";
    public static final String UPDATE = MaterialCategoryResource.PREFIX + ":update";
    public static final String DELETE = MaterialCategoryResource.PREFIX + ":delete";

    public static final String SYNC = MaterialCategoryResource.PREFIX + ":sync";

    private MaterialCategoryPermissions() {
    }
}

