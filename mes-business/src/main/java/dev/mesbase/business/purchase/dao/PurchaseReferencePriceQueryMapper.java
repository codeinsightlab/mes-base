package dev.mesbase.business.purchase.dao;

import dev.mesbase.business.purchase.domain.PurchaseReferencePriceExample;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceRow;
import java.util.List;

/**
 * Cross-table display query; persistence and predicates remain generated single-table capabilities.
 */
public interface PurchaseReferencePriceQueryMapper {
    List<PurchaseReferencePriceRow> selectWithNames(PurchaseReferencePriceExample example);
}
