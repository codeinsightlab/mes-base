// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro.controller;

import dev.mesbase.business.pro.domain.ProWorkorderResource;

public final class ProWorkorderPermissions {
    public static final String LIST = ProWorkorderResource.PREFIX + ":list";
    public static final String QUERY = ProWorkorderResource.PREFIX + ":query";
    public static final String CREATE = ProWorkorderResource.PREFIX + ":create";
    public static final String UPDATE = ProWorkorderResource.PREFIX + ":update";
    public static final String DELETE = ProWorkorderResource.PREFIX + ":delete";

    public static final String SYNC = ProWorkorderResource.PREFIX + ":sync";

    private ProWorkorderPermissions() {
    }
}

