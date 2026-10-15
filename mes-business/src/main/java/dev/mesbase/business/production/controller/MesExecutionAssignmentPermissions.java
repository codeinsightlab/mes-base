// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesExecutionAssignmentResource;

public final class MesExecutionAssignmentPermissions {
    public static final String LIST = MesExecutionAssignmentResource.PREFIX + ":list";
    public static final String QUERY = MesExecutionAssignmentResource.PREFIX + ":query";
    public static final String CREATE = MesExecutionAssignmentResource.PREFIX + ":create";
    public static final String UPDATE = MesExecutionAssignmentResource.PREFIX + ":update";
    public static final String DELETE = MesExecutionAssignmentResource.PREFIX + ":delete";


    private MesExecutionAssignmentPermissions() {
    }
}

