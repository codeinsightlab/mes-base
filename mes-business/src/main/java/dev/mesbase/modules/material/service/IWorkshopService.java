// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.service;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.domain.WorkshopCreateCommand;
import dev.mesbase.modules.material.domain.WorkshopEntity;
import dev.mesbase.modules.material.domain.WorkshopPage;
import dev.mesbase.modules.material.domain.WorkshopQueryDto;
import dev.mesbase.modules.material.domain.WorkshopUpdateCommand;
import dev.mesbase.modules.material.sync.ExternalWorkshop;
import dev.mesbase.modules.material.sync.WorkshopSyncResult;
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

