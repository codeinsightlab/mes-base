package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.WorkorderErpExtraPage;
import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.WorkorderErpExtraCreateCommand;
import dev.mesbase.business.production.domain.WorkorderErpExtraEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraQueryDto;
import dev.mesbase.business.production.domain.WorkorderErpExtraUpdateCommand;
import java.util.List;

/**
 * 工单外部身份 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWorkorderErpExtraService {
    WorkorderErpExtraPage selectPage(WorkorderErpExtraQueryDto query);

    WorkorderErpExtraEntity selectById(Long id);

    WorkorderErpExtraEntity insert(WorkorderErpExtraCreateCommand input);

    WorkorderErpExtraEntity updateById(Long id, WorkorderErpExtraUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

