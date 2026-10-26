// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller;

import dev.mesbase.business.sales.domain.MesSalesOrderItemResource;

public final class MesSalesOrderItemPermissions {
    public static final String LIST = MesSalesOrderItemResource.PREFIX + ":list";
    public static final String QUERY = MesSalesOrderItemResource.PREFIX + ":query";
    public static final String CREATE = MesSalesOrderItemResource.PREFIX + ":create";
    public static final String UPDATE = MesSalesOrderItemResource.PREFIX + ":update";
    public static final String DELETE = MesSalesOrderItemResource.PREFIX + ":delete";


    private MesSalesOrderItemPermissions() {
    }
}

