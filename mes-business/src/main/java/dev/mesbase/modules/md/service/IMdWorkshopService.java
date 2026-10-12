// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.service;

import dev.mesbase.modules.md.domain.MdWorkshopCreateCommand;
import dev.mesbase.modules.md.domain.MdWorkshopEntity;
import dev.mesbase.modules.md.domain.MdWorkshopPage;
import dev.mesbase.modules.md.domain.MdWorkshopQueryDto;
import dev.mesbase.modules.md.domain.MdWorkshopUpdateCommand;
import java.util.List;

/**
 * 车间 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMdWorkshopService {
    MdWorkshopPage selectPage(MdWorkshopQueryDto query);

    MdWorkshopEntity selectById(Long id);

    MdWorkshopEntity insert(MdWorkshopCreateCommand input);

    MdWorkshopEntity updateById(Long id, MdWorkshopUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

