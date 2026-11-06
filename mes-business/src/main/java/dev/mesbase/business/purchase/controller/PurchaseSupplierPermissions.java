// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.purchase.controller;

import dev.mesbase.business.purchase.domain.PurchaseSupplierResource;

public final class PurchaseSupplierPermissions {
    public static final String LIST = PurchaseSupplierResource.PREFIX + ":list";
    public static final String QUERY = PurchaseSupplierResource.PREFIX + ":query";
    public static final String CREATE = PurchaseSupplierResource.PREFIX + ":create";
    public static final String UPDATE = PurchaseSupplierResource.PREFIX + ":update";
    public static final String DELETE = PurchaseSupplierResource.PREFIX + ":delete";


    private PurchaseSupplierPermissions() {
    }
}
