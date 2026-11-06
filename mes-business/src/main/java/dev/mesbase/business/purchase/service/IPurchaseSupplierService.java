package dev.mesbase.business.purchase.service;

import dev.mesbase.business.purchase.domain.PurchaseSupplierCreateCommand;
import dev.mesbase.business.purchase.domain.PurchaseSupplierEntity;
import dev.mesbase.business.purchase.domain.PurchaseSupplierPage;
import dev.mesbase.business.purchase.domain.PurchaseSupplierQueryDto;
import dev.mesbase.business.purchase.domain.PurchaseSupplierUpdateCommand;
import java.util.List;

/**
 * 供应商 CRUD 用例。
 *
 * @author MES Base
 */
public interface IPurchaseSupplierService {
    PurchaseSupplierPage selectPage(PurchaseSupplierQueryDto query);

    PurchaseSupplierEntity selectById(Long id);

    PurchaseSupplierEntity insert(PurchaseSupplierCreateCommand input);

    PurchaseSupplierEntity updateById(Long id, PurchaseSupplierUpdateCommand input);

    PurchaseSupplierEntity lock(Long id, boolean enabled);

    void deleteByIds(List<Long> ids);
}
