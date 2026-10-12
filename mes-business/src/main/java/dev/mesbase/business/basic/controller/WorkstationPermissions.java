// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.domain.WorkstationResource;

public final class WorkstationPermissions {
    public static final String LIST = WorkstationResource.PREFIX + ":list";
    public static final String QUERY = WorkstationResource.PREFIX + ":query";
    public static final String CREATE = WorkstationResource.PREFIX + ":create";
    public static final String UPDATE = WorkstationResource.PREFIX + ":update";
    public static final String DELETE = WorkstationResource.PREFIX + ":delete";

    public static final String SYNC = WorkstationResource.PREFIX + ":sync";

    private WorkstationPermissions() {
    }
}

