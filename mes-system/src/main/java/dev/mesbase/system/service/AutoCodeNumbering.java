package dev.mesbase.system.service;

import java.util.List;

/** Public business entry for ruleCode-based, factory-scoped document numbers. */
public interface AutoCodeNumbering {
    String getDocNo(String type);

    String getDocNo(String ruleCode, String expectedRuleType);

    List<RuleOption> rules(String ruleType);

    String uniqueRuleCode(String ruleType);

    record RuleOption(String ruleCode, String ruleName, String prefix) { }
}
