package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionQueryDto;
import dev.mesbase.business.production.domain.MesProductionExecutionUpdateCommand;
import java.util.List;

/**
 * 生产执行单元 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesProductionExecutionService {
    Page<MesProductionExecutionEntity> selectPage(MesProductionExecutionQueryDto query);

    MesProductionExecutionEntity selectById(Long id);

    MesProductionExecutionEntity insert(MesProductionExecutionCreateCommand input);

    MesProductionExecutionEntity updateById(Long id, MesProductionExecutionUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

