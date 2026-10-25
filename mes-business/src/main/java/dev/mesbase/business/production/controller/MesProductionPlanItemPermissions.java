// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesProductionPlanItemResource;

public final class MesProductionPlanItemPermissions {
    public static final String LIST = MesProductionPlanItemResource.PREFIX + ":list";
    public static final String QUERY = MesProductionPlanItemResource.PREFIX + ":query";
    public static final String CREATE = MesProductionPlanItemResource.PREFIX + ":create";
    public static final String UPDATE = MesProductionPlanItemResource.PREFIX + ":update";
    public static final String DELETE = MesProductionPlanItemResource.PREFIX + ":delete";


    private MesProductionPlanItemPermissions() {
    }
}

