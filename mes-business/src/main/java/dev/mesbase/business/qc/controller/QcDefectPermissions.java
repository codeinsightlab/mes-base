// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.controller;

import dev.mesbase.business.qc.domain.QcDefectResource;

public final class QcDefectPermissions {
    public static final String LIST = QcDefectResource.PREFIX + ":list";
    public static final String QUERY = QcDefectResource.PREFIX + ":query";
    public static final String CREATE = QcDefectResource.PREFIX + ":create";
    public static final String UPDATE = QcDefectResource.PREFIX + ":update";
    public static final String DELETE = QcDefectResource.PREFIX + ":delete";

    public static final String SYNC = QcDefectResource.PREFIX + ":sync";

    private QcDefectPermissions() {
    }
}

