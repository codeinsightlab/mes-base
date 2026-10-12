// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.controller;

import dev.mesbase.business.md.domain.MdWorkshopResource;

public final class MdWorkshopPermissions {
    public static final String LIST = MdWorkshopResource.PREFIX + ":list";
    public static final String QUERY = MdWorkshopResource.PREFIX + ":query";
    public static final String CREATE = MdWorkshopResource.PREFIX + ":create";
    public static final String UPDATE = MdWorkshopResource.PREFIX + ":update";
    public static final String DELETE = MdWorkshopResource.PREFIX + ":delete";

    public static final String SYNC = MdWorkshopResource.PREFIX + ":sync";

    private MdWorkshopPermissions() {
    }
}

