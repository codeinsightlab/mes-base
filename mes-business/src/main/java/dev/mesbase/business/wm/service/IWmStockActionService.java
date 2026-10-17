package dev.mesbase.business.wm.service;

import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionPage;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;

/**
 * 本地仓储不可变收发存动作事实只读查询用例。
 *
 * @author MES Base
 */
public interface IWmStockActionService {
    WmStockActionPage selectPage(WmStockActionQueryDto query);

    WmStockActionEntity selectById(String id);

}
