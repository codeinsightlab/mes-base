// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesExecutionCompletionResource;

public final class MesExecutionCompletionPermissions {
    public static final String LIST = MesExecutionCompletionResource.PREFIX + ":list";
    public static final String QUERY = MesExecutionCompletionResource.PREFIX + ":query";
    public static final String CREATE = MesExecutionCompletionResource.PREFIX + ":create";
    public static final String UPDATE = MesExecutionCompletionResource.PREFIX + ":update";
    public static final String DELETE = MesExecutionCompletionResource.PREFIX + ":delete";


    private MesExecutionCompletionPermissions() {
    }
}

