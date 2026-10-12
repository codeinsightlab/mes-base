// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.domain.SafetyStockResource;

public final class SafetyStockPermissions {
    public static final String LIST = SafetyStockResource.PREFIX + ":list";
    public static final String QUERY = SafetyStockResource.PREFIX + ":query";
    public static final String CREATE = SafetyStockResource.PREFIX + ":create";
    public static final String UPDATE = SafetyStockResource.PREFIX + ":update";
    public static final String DELETE = SafetyStockResource.PREFIX + ":delete";

    public static final String SYNC = SafetyStockResource.PREFIX + ":sync";

    private SafetyStockPermissions() {
    }
}

