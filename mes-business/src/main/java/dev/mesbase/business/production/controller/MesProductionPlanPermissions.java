// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesProductionPlanResource;

public final class MesProductionPlanPermissions {
    public static final String LIST = MesProductionPlanResource.PREFIX + ":list";
    public static final String QUERY = MesProductionPlanResource.PREFIX + ":query";
    public static final String CREATE = MesProductionPlanResource.PREFIX + ":create";
    public static final String UPDATE = MesProductionPlanResource.PREFIX + ":update";
    public static final String DELETE = MesProductionPlanResource.PREFIX + ":delete";


    private MesProductionPlanPermissions() {
    }
}

