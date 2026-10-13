// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.controller;

import dev.mesbase.business.issue.domain.MesMaterialIssueResource;

public final class MesMaterialIssuePermissions {
    public static final String LIST = MesMaterialIssueResource.PREFIX + ":list";
    public static final String QUERY = MesMaterialIssueResource.PREFIX + ":query";
    public static final String CREATE = MesMaterialIssueResource.PREFIX + ":create";
    public static final String UPDATE = MesMaterialIssueResource.PREFIX + ":update";
    public static final String DELETE = MesMaterialIssueResource.PREFIX + ":delete";


    private MesMaterialIssuePermissions() {
    }
}

