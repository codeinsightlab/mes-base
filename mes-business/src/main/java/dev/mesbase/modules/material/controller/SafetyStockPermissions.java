// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.domain.SafetyStockResource;

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

