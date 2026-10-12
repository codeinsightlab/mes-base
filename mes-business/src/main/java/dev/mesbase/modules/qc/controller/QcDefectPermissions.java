// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller;


public final class QcDefectPermissions {
    public static final String READ = "business:qc:defect:read";
    public static final String CREATE = "business:qc:defect:create";
    public static final String UPDATE = "business:qc:defect:update";
    public static final String DELETE = "business:qc:defect:delete";

    public static final String SYNC = "business:qc:defect:sync";

    private QcDefectPermissions() {
    }
}

