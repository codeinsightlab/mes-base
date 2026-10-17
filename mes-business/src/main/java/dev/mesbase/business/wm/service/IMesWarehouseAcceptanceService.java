package dev.mesbase.business.wm.service;

import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptancePage;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceQueryDto;

/**
 * 成品仓储接收事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesWarehouseAcceptanceService {
    MesWarehouseAcceptancePage selectPage(MesWarehouseAcceptanceQueryDto query);

    MesWarehouseAcceptanceEntity selectById(String id);


}

