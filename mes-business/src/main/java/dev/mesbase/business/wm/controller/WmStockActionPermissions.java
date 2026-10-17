package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.WmStockActionResource;

public final class WmStockActionPermissions {
    public static final String LIST = WmStockActionResource.PREFIX + ":list";
    public static final String QUERY = WmStockActionResource.PREFIX + ":query";


    private WmStockActionPermissions() {
    }
}

