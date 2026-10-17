// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.service;

import dev.mesbase.business.wm.domain.WmStockActionCreateCommand;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionPage;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.domain.WmStockActionUpdateCommand;
import java.util.List;

/**
 * 本地仓储不可变收发存动作事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWmStockActionService {
    WmStockActionPage selectPage(WmStockActionQueryDto query);

    WmStockActionEntity selectById(String id);

    WmStockActionEntity insert(WmStockActionCreateCommand input);

    WmStockActionEntity updateById(String id, WmStockActionUpdateCommand input);

    void deleteByIds(List<String> ids);
}

