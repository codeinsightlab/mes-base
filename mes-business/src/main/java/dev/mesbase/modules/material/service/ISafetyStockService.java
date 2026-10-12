// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.service;

import dev.mesbase.modules.material.domain.SafetyStockCreateCommand;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockPage;
import dev.mesbase.modules.material.domain.SafetyStockQueryDto;
import dev.mesbase.modules.material.domain.SafetyStockUpdateCommand;
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
}

