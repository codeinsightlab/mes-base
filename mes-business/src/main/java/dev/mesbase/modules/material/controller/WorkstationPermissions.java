// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;


public final class WorkstationPermissions {
    public static final String READ = "business:workstation:read";
    public static final String CREATE = "business:workstation:create";
    public static final String UPDATE = "business:workstation:update";
    public static final String DELETE = "business:workstation:delete";

    public static final String SYNC = "business:workstation:sync";

    private WorkstationPermissions() {
    }
}

