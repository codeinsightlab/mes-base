package dev.mesbase.business.production.domain;

/**
 * Business fields carried by a production code; parsing and validation belong to ProductionCodeRules.
 */
public record ProductionScanCode(String workorderCode, String productCode, String batchNo) {
}
