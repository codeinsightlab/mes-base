// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.service;

import dev.mesbase.modules.md.domain.MdUnitMeasureCreateCommand;
import dev.mesbase.modules.md.domain.MdUnitMeasureEntity;
import dev.mesbase.modules.md.domain.MdUnitMeasurePage;
import dev.mesbase.modules.md.domain.MdUnitMeasureQueryDto;
import dev.mesbase.modules.md.domain.MdUnitMeasureUpdateCommand;
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
}

