package dev.mesbase.business.wm.domain;

/**
 * EXTRA_CODE_REASON: inventory list displays material/warehouse labels fetched in bounded batches.
 */
public record WmInventoryItem(WmInventoryEntity inventory, String materialName, String warehouseName,
                              String warehouseSource) {
}
