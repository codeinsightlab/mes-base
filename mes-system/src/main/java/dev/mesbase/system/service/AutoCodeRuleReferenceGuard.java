package dev.mesbase.system.service;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;

/** Checks document-number prefix references before a platform rule is edited or removed. */
public interface AutoCodeRuleReferenceGuard {
    /** Requires a usable bound factory when a rule is assigned to one. */
    void requireEnabledFactory(String factoryId);

    /** Returns the current fixed prefix only when no matching business document references it. */
    String requireUnreferenced(SysAutoCodeRule rule);

    /** Checks whether a leading FIXCHAR prefix can change without losing historical attribution. */
    void requirePrefixChangeUnreferenced(SysAutoCodeRule rule, String oldPrefix, String newPrefix);
}
