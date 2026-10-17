// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.WmStockActionResource;

public final class WmStockActionPermissions {
    public static final String LIST = WmStockActionResource.PREFIX + ":list";
    public static final String QUERY = WmStockActionResource.PREFIX + ":query";
    public static final String CREATE = WmStockActionResource.PREFIX + ":create";
    public static final String UPDATE = WmStockActionResource.PREFIX + ":update";
    public static final String DELETE = WmStockActionResource.PREFIX + ":delete";


    private WmStockActionPermissions() {
    }
}

