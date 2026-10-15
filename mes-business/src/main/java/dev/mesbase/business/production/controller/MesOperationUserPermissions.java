// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesOperationUserResource;

public final class MesOperationUserPermissions {
    public static final String LIST = MesOperationUserResource.PREFIX + ":list";
    public static final String QUERY = MesOperationUserResource.PREFIX + ":query";
    public static final String CREATE = MesOperationUserResource.PREFIX + ":create";
    public static final String UPDATE = MesOperationUserResource.PREFIX + ":update";
    public static final String DELETE = MesOperationUserResource.PREFIX + ":delete";


    private MesOperationUserPermissions() {
    }
}

