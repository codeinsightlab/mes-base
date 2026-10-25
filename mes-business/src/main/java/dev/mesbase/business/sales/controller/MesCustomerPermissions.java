// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.controller;

import dev.mesbase.business.sales.domain.MesCustomerResource;

public final class MesCustomerPermissions {
    public static final String LIST = MesCustomerResource.PREFIX + ":list";
    public static final String QUERY = MesCustomerResource.PREFIX + ":query";
    public static final String CREATE = MesCustomerResource.PREFIX + ":create";
    public static final String UPDATE = MesCustomerResource.PREFIX + ":update";
    public static final String DELETE = MesCustomerResource.PREFIX + ":delete";


    private MesCustomerPermissions() {
    }
}

