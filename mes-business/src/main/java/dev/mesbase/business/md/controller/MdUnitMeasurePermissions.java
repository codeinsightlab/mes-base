// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.controller;

import dev.mesbase.business.md.domain.MdUnitMeasureResource;

public final class MdUnitMeasurePermissions {
    public static final String LIST = MdUnitMeasureResource.PREFIX + ":list";
    public static final String QUERY = MdUnitMeasureResource.PREFIX + ":query";
    public static final String CREATE = MdUnitMeasureResource.PREFIX + ":create";
    public static final String UPDATE = MdUnitMeasureResource.PREFIX + ":update";
    public static final String DELETE = MdUnitMeasureResource.PREFIX + ":delete";

    public static final String SYNC = MdUnitMeasureResource.PREFIX + ":sync";

    private MdUnitMeasurePermissions() {
    }
}

