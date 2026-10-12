// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.service;

import dev.mesbase.business.md.domain.MdWorkshopCreateCommand;
import dev.mesbase.business.md.domain.MdWorkshopEntity;
import dev.mesbase.business.md.domain.MdWorkshopPage;
import dev.mesbase.business.md.domain.MdWorkshopQueryDto;
import dev.mesbase.business.md.domain.MdWorkshopUpdateCommand;
import dev.mesbase.business.md.sync.ExternalMdWorkshop;
import dev.mesbase.business.md.sync.MdWorkshopSyncResult;
import dev.mesbase.core.factory.FactoryScope;
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

    MdWorkshopSyncResult sync(FactoryScope scope);

    MdWorkshopSyncResult upsert(FactoryScope scope,
                                List<ExternalMdWorkshop> inputs);
}

