// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.controller;

import dev.mesbase.business.issue.domain.MesMaterialIssueItemResource;

public final class MesMaterialIssueItemPermissions {
    public static final String LIST = MesMaterialIssueItemResource.PREFIX + ":list";
    public static final String QUERY = MesMaterialIssueItemResource.PREFIX + ":query";
    public static final String CREATE = MesMaterialIssueItemResource.PREFIX + ":create";
    public static final String UPDATE = MesMaterialIssueItemResource.PREFIX + ":update";
    public static final String DELETE = MesMaterialIssueItemResource.PREFIX + ":delete";


    private MesMaterialIssueItemPermissions() {
    }
}

