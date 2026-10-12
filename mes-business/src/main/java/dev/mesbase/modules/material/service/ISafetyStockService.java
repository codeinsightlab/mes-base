// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.service;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.domain.SafetyStockCreateCommand;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockPage;
import dev.mesbase.modules.material.domain.SafetyStockQueryDto;
import dev.mesbase.modules.material.domain.SafetyStockUpdateCommand;
import dev.mesbase.modules.material.sync.ExternalSafetyStock;
import dev.mesbase.modules.material.sync.SafetyStockSyncResult;
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

