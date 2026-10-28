package dev.mesbase.system.service;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;

/** Checks legacy document-number references before a platform rule can be removed. */
public interface AutoCodeRuleReferenceGuard {
    String requireUnreferenced(SysAutoCodeRule rule);

    /** Checks whether a leading FIXCHAR prefix can change without losing historical attribution. */
    void requirePrefixChangeUnreferenced(SysAutoCodeRule rule, String oldPrefix, String newPrefix);
}
