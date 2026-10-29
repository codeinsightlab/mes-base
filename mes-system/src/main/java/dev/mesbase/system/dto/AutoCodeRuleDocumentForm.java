package dev.mesbase.system.dto;

/** One command saves the rule and its standard numbering configuration. */
public record AutoCodeRuleDocumentForm(AutoCodeRuleForm rule, String prefix, String version) { }
