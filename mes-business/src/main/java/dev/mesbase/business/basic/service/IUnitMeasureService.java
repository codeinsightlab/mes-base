// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.domain.UnitMeasureCreateCommand;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasurePage;
import dev.mesbase.business.basic.domain.UnitMeasureQueryDto;
import dev.mesbase.business.basic.domain.UnitMeasureUpdateCommand;
import dev.mesbase.business.basic.sync.ExternalUnitMeasure;
import dev.mesbase.business.basic.sync.UnitMeasureSyncResult;
import dev.mesbase.core.factory.FactoryScope;
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

