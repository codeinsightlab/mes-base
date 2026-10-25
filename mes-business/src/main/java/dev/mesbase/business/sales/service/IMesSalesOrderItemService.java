// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.service;

import dev.mesbase.business.sales.domain.MesSalesOrderItemCreateCommand;
import dev.mesbase.business.sales.domain.MesSalesOrderItemEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemPage;
import dev.mesbase.business.sales.domain.MesSalesOrderItemQueryDto;
import dev.mesbase.business.sales.domain.MesSalesOrderItemUpdateCommand;
import java.util.List;

/**
 * 销售订单明细 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesSalesOrderItemService {
    MesSalesOrderItemPage selectPage(MesSalesOrderItemQueryDto query);

    MesSalesOrderItemEntity selectById(Long id);

    MesSalesOrderItemEntity insert(MesSalesOrderItemCreateCommand input);

    MesSalesOrderItemEntity updateById(Long id, MesSalesOrderItemUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

