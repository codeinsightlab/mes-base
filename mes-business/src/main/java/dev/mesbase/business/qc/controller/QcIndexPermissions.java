// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.controller;

import dev.mesbase.business.qc.domain.QcIndexResource;

public final class QcIndexPermissions {
    public static final String LIST = QcIndexResource.PREFIX + ":list";
    public static final String QUERY = QcIndexResource.PREFIX + ":query";
    public static final String CREATE = QcIndexResource.PREFIX + ":create";
    public static final String UPDATE = QcIndexResource.PREFIX + ":update";
    public static final String DELETE = QcIndexResource.PREFIX + ":delete";

    public static final String SYNC = QcIndexResource.PREFIX + ":sync";

    private QcIndexPermissions() {
    }
}

