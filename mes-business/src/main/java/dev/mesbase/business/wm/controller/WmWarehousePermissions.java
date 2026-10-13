// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.WmWarehouseResource;

public final class WmWarehousePermissions {
    public static final String LIST = WmWarehouseResource.PREFIX + ":list";
    public static final String QUERY = WmWarehouseResource.PREFIX + ":query";
    public static final String CREATE = WmWarehouseResource.PREFIX + ":create";
    public static final String UPDATE = WmWarehouseResource.PREFIX + ":update";
    public static final String DELETE = WmWarehouseResource.PREFIX + ":delete";

    public static final String SYNC = WmWarehouseResource.PREFIX + ":sync";

    private WmWarehousePermissions() {
    }
}

