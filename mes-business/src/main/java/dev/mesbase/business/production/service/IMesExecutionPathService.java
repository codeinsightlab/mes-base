package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.MesExecutionPathCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathQueryDto;
import dev.mesbase.business.production.domain.MesExecutionPathUpdateCommand;
import java.util.List;

/**
 * 生产执行当前完整路径 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesExecutionPathService {
    Page<MesExecutionPathEntity> selectPage(MesExecutionPathQueryDto query);

    MesExecutionPathEntity selectById(Long id);

    MesExecutionPathEntity insert(MesExecutionPathCreateCommand input);

    MesExecutionPathEntity updateById(Long id, MesExecutionPathUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

