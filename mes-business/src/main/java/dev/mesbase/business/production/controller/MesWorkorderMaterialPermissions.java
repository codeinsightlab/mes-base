// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesWorkorderMaterialResource;

public final class MesWorkorderMaterialPermissions {
    public static final String LIST = MesWorkorderMaterialResource.PREFIX + ":list";
    public static final String QUERY = MesWorkorderMaterialResource.PREFIX + ":query";
    public static final String CREATE = MesWorkorderMaterialResource.PREFIX + ":create";
    public static final String UPDATE = MesWorkorderMaterialResource.PREFIX + ":update";
    public static final String DELETE = MesWorkorderMaterialResource.PREFIX + ":delete";


    private MesWorkorderMaterialPermissions() {
    }
}

