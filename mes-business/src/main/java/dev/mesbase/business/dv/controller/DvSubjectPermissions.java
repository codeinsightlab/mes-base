// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.dv.controller;

import dev.mesbase.business.dv.domain.DvSubjectResource;

public final class DvSubjectPermissions {
    public static final String LIST = DvSubjectResource.PREFIX + ":list";
    public static final String QUERY = DvSubjectResource.PREFIX + ":query";
    public static final String CREATE = DvSubjectResource.PREFIX + ":create";
    public static final String UPDATE = DvSubjectResource.PREFIX + ":update";
    public static final String DELETE = DvSubjectResource.PREFIX + ":delete";

    public static final String SYNC = DvSubjectResource.PREFIX + ":sync";

    private DvSubjectPermissions() {
    }
}

