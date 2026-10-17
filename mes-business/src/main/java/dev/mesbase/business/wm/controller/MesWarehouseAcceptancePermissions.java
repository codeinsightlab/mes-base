// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceResource;

public final class MesWarehouseAcceptancePermissions {
    public static final String LIST = MesWarehouseAcceptanceResource.PREFIX + ":list";
    public static final String QUERY = MesWarehouseAcceptanceResource.PREFIX + ":query";
    public static final String CREATE = MesWarehouseAcceptanceResource.PREFIX + ":create";
    public static final String UPDATE = MesWarehouseAcceptanceResource.PREFIX + ":update";
    public static final String DELETE = MesWarehouseAcceptanceResource.PREFIX + ":delete";


    private MesWarehouseAcceptancePermissions() {
    }
}

