// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.domain.MaterialResource;

public final class MaterialPermissions {
    public static final String LIST = MaterialResource.PREFIX + ":list";
    public static final String QUERY = MaterialResource.PREFIX + ":query";
    public static final String CREATE = MaterialResource.PREFIX + ":create";
    public static final String UPDATE = MaterialResource.PREFIX + ":update";
    public static final String DELETE = MaterialResource.PREFIX + ":delete";

    public static final String SYNC = MaterialResource.PREFIX + ":sync";

    private MaterialPermissions() {
    }
}

