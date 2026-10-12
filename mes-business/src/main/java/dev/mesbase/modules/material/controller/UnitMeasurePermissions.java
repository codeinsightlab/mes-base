// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.domain.UnitMeasureResource;

public final class UnitMeasurePermissions {
    public static final String LIST = UnitMeasureResource.PREFIX + ":list";
    public static final String QUERY = UnitMeasureResource.PREFIX + ":query";
    public static final String CREATE = UnitMeasureResource.PREFIX + ":create";
    public static final String UPDATE = UnitMeasureResource.PREFIX + ":update";
    public static final String DELETE = UnitMeasureResource.PREFIX + ":delete";

    public static final String SYNC = UnitMeasureResource.PREFIX + ":sync";

    private UnitMeasurePermissions() {
    }
}

