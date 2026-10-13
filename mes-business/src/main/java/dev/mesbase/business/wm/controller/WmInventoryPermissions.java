// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.WmInventoryResource;

public final class WmInventoryPermissions {
    public static final String LIST = WmInventoryResource.PREFIX + ":list";
    public static final String QUERY = WmInventoryResource.PREFIX + ":query";
    public static final String CREATE = WmInventoryResource.PREFIX + ":create";
    public static final String UPDATE = WmInventoryResource.PREFIX + ":update";
    public static final String DELETE = WmInventoryResource.PREFIX + ":delete";

    public static final String SYNC = WmInventoryResource.PREFIX + ":sync";

    private WmInventoryPermissions() {
    }
}

