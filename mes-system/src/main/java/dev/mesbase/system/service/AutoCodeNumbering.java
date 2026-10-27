package dev.mesbase.system.service;

/** Public business entry for ruleCode-based, factory-scoped document numbers. */
public interface AutoCodeNumbering {
    String getDocNo(String type);
}
