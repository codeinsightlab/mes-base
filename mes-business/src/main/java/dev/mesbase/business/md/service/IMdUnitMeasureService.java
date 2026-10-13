// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.service;

import dev.mesbase.business.md.domain.MdUnitMeasureCreateCommand;
import dev.mesbase.business.md.domain.MdUnitMeasureEntity;
import dev.mesbase.business.md.domain.MdUnitMeasurePage;
import dev.mesbase.business.md.domain.MdUnitMeasureQueryDto;
import dev.mesbase.business.md.domain.MdUnitMeasureUpdateCommand;
import dev.mesbase.business.md.sync.ExternalMdUnitMeasure;
import dev.mesbase.business.md.sync.MdUnitMeasureSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 计量单位 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMdUnitMeasureService {
    MdUnitMeasurePage selectPage(MdUnitMeasureQueryDto query);

    MdUnitMeasureEntity selectById(Long id);

    MdUnitMeasureEntity insert(MdUnitMeasureCreateCommand input);

    MdUnitMeasureEntity updateById(Long id, MdUnitMeasureUpdateCommand input);

    void deleteByIds(List<Long> ids);
    MdUnitMeasureSyncResult sync(FactoryScope scope);

    MdUnitMeasureSyncResult upsert(FactoryScope scope,
            List<ExternalMdUnitMeasure> inputs);
}

