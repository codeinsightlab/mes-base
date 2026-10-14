// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesProductionExecutionResource;

public final class MesProductionExecutionPermissions {
    public static final String LIST = MesProductionExecutionResource.PREFIX + ":list";
    public static final String QUERY = MesProductionExecutionResource.PREFIX + ":query";
    public static final String CREATE = MesProductionExecutionResource.PREFIX + ":create";
    public static final String UPDATE = MesProductionExecutionResource.PREFIX + ":update";
    public static final String DELETE = MesProductionExecutionResource.PREFIX + ":delete";


    private MesProductionExecutionPermissions() {
    }
}

