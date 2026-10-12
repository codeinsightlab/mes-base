// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.domain.WorkshopCreateCommand;
import dev.mesbase.business.basic.domain.WorkshopEntity;
import dev.mesbase.business.basic.domain.WorkshopPage;
import dev.mesbase.business.basic.domain.WorkshopQueryDto;
import dev.mesbase.business.basic.domain.WorkshopUpdateCommand;
import dev.mesbase.business.basic.sync.ExternalWorkshop;
import dev.mesbase.business.basic.sync.WorkshopSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 车间 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWorkshopService {
    WorkshopPage selectPage(WorkshopQueryDto query);

    WorkshopEntity selectById(Long id);

    WorkshopEntity insert(WorkshopCreateCommand input);

    WorkshopEntity updateById(Long id, WorkshopUpdateCommand input);

    void deleteByIds(List<Long> ids);

    WorkshopSyncResult sync(FactoryScope scope);

    WorkshopSyncResult upsert(FactoryScope scope,
                              List<ExternalWorkshop> inputs);
}

