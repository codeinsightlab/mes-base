// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.domain.EquipmentResource;

public final class EquipmentPermissions {
    public static final String READ = EquipmentResource.PREFIX + ":read";
    public static final String CREATE = EquipmentResource.PREFIX + ":create";
    public static final String UPDATE = EquipmentResource.PREFIX + ":update";
    public static final String DELETE = EquipmentResource.PREFIX + ":delete";


    private EquipmentPermissions() {
    }
}

