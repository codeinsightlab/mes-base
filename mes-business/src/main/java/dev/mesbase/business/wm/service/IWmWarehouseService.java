package dev.mesbase.business.wm.service;

import dev.mesbase.business.shared.ExternalSyncCheck;
import dev.mesbase.business.wm.domain.WmWarehouseCreateCommand;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehousePage;
import dev.mesbase.business.wm.domain.WmWarehouseQueryDto;
import dev.mesbase.business.wm.domain.WmWarehouseUpdateCommand;
import dev.mesbase.business.wm.sync.ExternalWmWarehouse;
import dev.mesbase.business.wm.sync.WmWarehouseSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import java.util.List;

/**
 * 仓库管理 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWmWarehouseService {
    WmWarehousePage selectPage(WmWarehouseQueryDto query);

    WmWarehouseEntity selectById(Long id);

    WmWarehouseEntity insert(WmWarehouseCreateCommand input);

    WmWarehouseEntity updateById(Long id, WmWarehouseUpdateCommand input);

    void deleteByIds(List<Long> ids);

    ExternalSyncCheck inspectExternal(ExternalWmWarehouse input);

    WmWarehouseSyncResult sync(FactoryScope scope);

    WmWarehouseSyncResult upsert(FactoryScope scope,
                                 List<ExternalWmWarehouse> inputs, SyncCoordinator.Run run);
}

