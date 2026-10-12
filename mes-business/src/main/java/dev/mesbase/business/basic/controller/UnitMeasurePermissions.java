// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.domain.UnitMeasureResource;

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

