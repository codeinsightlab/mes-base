// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.service;

import dev.mesbase.business.sales.domain.MesCustomerCreateCommand;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesCustomerPage;
import dev.mesbase.business.sales.domain.MesCustomerQueryDto;
import dev.mesbase.business.sales.domain.MesCustomerUpdateCommand;
import java.util.List;

/**
 * 销售客户 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesCustomerService {
    MesCustomerPage selectPage(MesCustomerQueryDto query);

    MesCustomerEntity selectById(Long id);

    MesCustomerEntity insert(MesCustomerCreateCommand input);

    MesCustomerEntity updateById(Long id, MesCustomerUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

