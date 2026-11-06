package dev.mesbase.business.purchase.controller;

import dev.mesbase.business.purchase.domain.PurchaseReferencePriceResource;

public final class PurchaseReferencePricePermissions {
    public static final String LIST = PurchaseReferencePriceResource.PREFIX + ":list";
    public static final String QUERY = PurchaseReferencePriceResource.PREFIX + ":query";
    public static final String CREATE = PurchaseReferencePriceResource.PREFIX + ":create";


    private PurchaseReferencePricePermissions() {
    }
}
