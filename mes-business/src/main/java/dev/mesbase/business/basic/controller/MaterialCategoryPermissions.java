// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.domain.MaterialCategoryResource;

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

