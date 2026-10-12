// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.domain.WorkshopResource;

public final class WorkshopPermissions {
    public static final String LIST = WorkshopResource.PREFIX + ":list";
    public static final String QUERY = WorkshopResource.PREFIX + ":query";
    public static final String CREATE = WorkshopResource.PREFIX + ":create";
    public static final String UPDATE = WorkshopResource.PREFIX + ":update";
    public static final String DELETE = WorkshopResource.PREFIX + ":delete";

    public static final String SYNC = WorkshopResource.PREFIX + ":sync";

    private WorkshopPermissions() {
    }
}

