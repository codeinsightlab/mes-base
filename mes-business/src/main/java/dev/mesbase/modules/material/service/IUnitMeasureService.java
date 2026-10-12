// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.service;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.domain.UnitMeasureCreateCommand;
import dev.mesbase.modules.material.domain.UnitMeasureEntity;
import dev.mesbase.modules.material.domain.UnitMeasurePage;
import dev.mesbase.modules.material.domain.UnitMeasureQueryDto;
import dev.mesbase.modules.material.domain.UnitMeasureUpdateCommand;
import dev.mesbase.modules.material.sync.ExternalUnitMeasure;
import dev.mesbase.modules.material.sync.UnitMeasureSyncResult;
import java.util.List;

/**
 * 计量单位 CRUD 用例。
 *
 * @author MES Base
 */
public interface IUnitMeasureService {
    UnitMeasurePage selectPage(UnitMeasureQueryDto query);

    UnitMeasureEntity selectById(Long id);

    UnitMeasureEntity insert(UnitMeasureCreateCommand input);

    UnitMeasureEntity updateById(Long id, UnitMeasureUpdateCommand input);

    void deleteByIds(List<Long> ids);

    UnitMeasureSyncResult sync(FactoryScope scope);

    UnitMeasureSyncResult upsert(FactoryScope scope,
                                 List<ExternalUnitMeasure> inputs);
}

