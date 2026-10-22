package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.domain.SafetyStockCreateCommand;
import dev.mesbase.business.basic.domain.SafetyStockEntity;
import dev.mesbase.business.basic.domain.SafetyStockPage;
import dev.mesbase.business.basic.domain.SafetyStockQueryDto;
import dev.mesbase.business.basic.domain.SafetyStockUpdateCommand;
import dev.mesbase.business.basic.sync.ExternalSafetyStock;
import dev.mesbase.business.basic.sync.SafetyStockSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 安全库存配置 CRUD 用例。
 *
 * @author MES Base
 */
public interface ISafetyStockService {
    SafetyStockPage selectPage(SafetyStockQueryDto query);

    SafetyStockEntity selectById(Long id);

    SafetyStockEntity insert(SafetyStockCreateCommand input);

    SafetyStockEntity updateById(Long id, SafetyStockUpdateCommand input);

    void deleteByIds(List<Long> ids);

    SafetyStockSyncResult sync(FactoryScope scope);

    SafetyStockSyncResult upsert(FactoryScope scope,
                                 List<ExternalSafetyStock> inputs);
}

