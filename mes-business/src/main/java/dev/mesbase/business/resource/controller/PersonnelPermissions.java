// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.controller;

import dev.mesbase.business.resource.domain.PersonnelResource;

public final class PersonnelPermissions {
    public static final String READ = PersonnelResource.PREFIX + ":read";
    public static final String CREATE = PersonnelResource.PREFIX + ":create";
    public static final String UPDATE = PersonnelResource.PREFIX + ":update";
    public static final String DELETE = PersonnelResource.PREFIX + ":delete";


    private PersonnelPermissions() {
    }
}

