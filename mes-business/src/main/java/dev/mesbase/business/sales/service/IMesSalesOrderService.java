// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.service;

import dev.mesbase.business.sales.domain.MesSalesOrderCreateCommand;
import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderPage;
import dev.mesbase.business.sales.domain.MesSalesOrderQueryDto;
import dev.mesbase.business.sales.domain.MesSalesOrderUpdateCommand;
import java.util.List;

/**
 * 销售订单 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesSalesOrderService {
    MesSalesOrderPage selectPage(MesSalesOrderQueryDto query);

    MesSalesOrderEntity selectById(Long id);

    MesSalesOrderEntity insert(MesSalesOrderCreateCommand input);

    MesSalesOrderEntity updateById(Long id, MesSalesOrderUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

