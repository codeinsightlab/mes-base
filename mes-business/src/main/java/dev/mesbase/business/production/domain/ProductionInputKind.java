package dev.mesbase.business.production.domain;

/**
 * Formal contribution recorded by the MES confirmation use case.
 */
public enum ProductionInputKind {
    PRODUCTION_INPUT;

    public String getValue() {
        return name();
    }
}
