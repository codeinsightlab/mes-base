// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller;


public final class QcIndexPermissions {
    public static final String READ = "business:qc:index:read";
    public static final String CREATE = "business:qc:index:create";
    public static final String UPDATE = "business:qc:index:update";
    public static final String DELETE = "business:qc:index:delete";

    public static final String SYNC = "business:qc:index:sync";

    private QcIndexPermissions() {
    }
}

