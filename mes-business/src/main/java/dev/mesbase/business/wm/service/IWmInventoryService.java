package dev.mesbase.business.wm.service;

import dev.mesbase.business.wm.domain.WmInventoryCreateCommand;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryPage;
import dev.mesbase.business.wm.domain.WmInventoryQueryDto;
import dev.mesbase.business.wm.domain.WmInventoryUpdateCommand;
import dev.mesbase.business.wm.sync.ExternalWmInventory;
import dev.mesbase.business.wm.sync.WmInventorySyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;
import dev.mesbase.framework.sync.SyncCoordinator;

/**
 * 库存管理 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWmInventoryService {
    WmInventoryPage selectPage(WmInventoryQueryDto query);

    WmInventoryEntity selectById(Long id);

    WmInventoryEntity insert(WmInventoryCreateCommand input);

    WmInventoryEntity updateById(Long id, WmInventoryUpdateCommand input);

    void deleteByIds(List<Long> ids);

    WmInventorySyncResult sync(FactoryScope scope);

    WmInventorySyncResult upsert(FactoryScope scope,
                                 List<ExternalWmInventory> inputs, SyncCoordinator.Run run);
}

