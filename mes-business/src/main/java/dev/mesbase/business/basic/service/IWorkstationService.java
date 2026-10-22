package dev.mesbase.business.basic.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.basic.domain.WorkstationCreateCommand;
import dev.mesbase.business.basic.domain.WorkstationEntity;
import dev.mesbase.business.basic.domain.WorkstationQueryDto;
import dev.mesbase.business.basic.domain.WorkstationUpdateCommand;
import dev.mesbase.business.basic.sync.ExternalWorkstation;
import dev.mesbase.business.basic.sync.WorkstationSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 工作站 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWorkstationService {
    Page<WorkstationEntity> selectPage(WorkstationQueryDto query);

    WorkstationEntity selectById(Long id);

    WorkstationEntity insert(WorkstationCreateCommand input);

    WorkstationEntity updateById(Long id, WorkstationUpdateCommand input);

    void deleteByIds(List<Long> ids);
    WorkstationSyncResult sync(FactoryScope scope);

    WorkstationSyncResult upsert(FactoryScope scope,
            List<ExternalWorkstation> inputs);
}

