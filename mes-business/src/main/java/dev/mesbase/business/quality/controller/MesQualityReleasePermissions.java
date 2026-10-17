// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.domain.MesQualityReleaseResource;

public final class MesQualityReleasePermissions {
    public static final String LIST = MesQualityReleaseResource.PREFIX + ":list";
    public static final String QUERY = MesQualityReleaseResource.PREFIX + ":query";
    public static final String CREATE = MesQualityReleaseResource.PREFIX + ":create";
    public static final String UPDATE = MesQualityReleaseResource.PREFIX + ":update";
    public static final String DELETE = MesQualityReleaseResource.PREFIX + ":delete";


    private MesQualityReleasePermissions() {
    }
}

