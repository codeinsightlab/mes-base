package dev.mesbase.business.wm.domain;

/**
 * Read-only display label; quantities and unit admission remain owned by the inventory services.
 */
public record WmInventoryUnitLabel(Long stockId, String unitName) {
}
