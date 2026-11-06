package dev.mesbase.business.purchase.service;

import dev.mesbase.business.purchase.domain.PurchaseReferencePriceCreateCommand;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceEntity;
import dev.mesbase.business.purchase.domain.PurchaseReferencePricePage;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceQueryDto;
import java.util.List;

/**
 * 参考报价历史 CRUD 用例。
 *
 * @author MES Base
 */
public interface IPurchaseReferencePriceService {
    PurchaseReferencePricePage selectPage(PurchaseReferencePriceQueryDto query);

    PurchaseReferencePriceEntity selectById(Long id);

    PurchaseReferencePriceEntity insert(PurchaseReferencePriceCreateCommand input);

    record Preview(PurchaseReferencePriceEntity reference, java.math.BigDecimal amount) {
    }

    Preview preview(Long supplierId, Long materialId, java.time.LocalDate date,
                    java.math.BigDecimal quantity, java.math.BigDecimal unitPrice, String remark);

    PurchaseReferencePriceEntity effective(Long supplierId, Long materialId, java.time.LocalDate date);
}
