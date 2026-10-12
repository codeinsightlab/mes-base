// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;


public final class SafetyStockPermissions {
    public static final String READ = "business:safety:stock:read";
    public static final String CREATE = "business:safety:stock:create";
    public static final String UPDATE = "business:safety:stock:update";
    public static final String DELETE = "business:safety:stock:delete";

    public static final String SYNC = "business:safety:stock:sync";

    private SafetyStockPermissions() {
    }
}

