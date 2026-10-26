// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller;

import dev.mesbase.business.sales.domain.MesSalesOrderResource;

public final class MesSalesOrderPermissions {
    public static final String LIST = MesSalesOrderResource.PREFIX + ":list";
    public static final String QUERY = MesSalesOrderResource.PREFIX + ":query";
    public static final String CREATE = MesSalesOrderResource.PREFIX + ":create";
    public static final String UPDATE = MesSalesOrderResource.PREFIX + ":update";
    public static final String DELETE = MesSalesOrderResource.PREFIX + ":delete";


    private MesSalesOrderPermissions() {
    }
}

