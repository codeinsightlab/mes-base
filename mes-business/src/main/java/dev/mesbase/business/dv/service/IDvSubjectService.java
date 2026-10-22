package dev.mesbase.business.dv.service;

import dev.mesbase.business.dv.domain.DvSubjectPage;
import com.github.pagehelper.Page;
import dev.mesbase.business.dv.domain.DvSubjectCreateCommand;
import dev.mesbase.business.dv.domain.DvSubjectEntity;
import dev.mesbase.business.dv.domain.DvSubjectQueryDto;
import dev.mesbase.business.dv.domain.DvSubjectUpdateCommand;
import dev.mesbase.business.dv.sync.DvSubjectSyncResult;
import dev.mesbase.business.dv.sync.ExternalDvSubject;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 设备点检保养项目 CRUD 用例。
 *
 * @author MES Base
 */
public interface IDvSubjectService {
    DvSubjectPage selectPage(DvSubjectQueryDto query);

    DvSubjectEntity selectById(Long id);

    DvSubjectEntity insert(DvSubjectCreateCommand input);

    DvSubjectEntity updateById(Long id, DvSubjectUpdateCommand input);

    void deleteByIds(List<Long> ids);
    DvSubjectSyncResult sync(FactoryScope scope);

    DvSubjectSyncResult upsert(FactoryScope scope,
            List<ExternalDvSubject> inputs);
}

