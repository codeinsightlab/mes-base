package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesExecutionPathNodeCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodePage;
import dev.mesbase.business.production.domain.MesExecutionPathNodeQueryDto;
import dev.mesbase.business.production.domain.MesExecutionPathNodeUpdateCommand;
import java.util.List;

/**
 * 生产执行选定路径的有序节点引用 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesExecutionPathNodeService {
    MesExecutionPathNodePage selectPage(MesExecutionPathNodeQueryDto query);

    MesExecutionPathNodeEntity selectById(Long id);

    MesExecutionPathNodeEntity insert(MesExecutionPathNodeCreateCommand input);

    MesExecutionPathNodeEntity updateById(Long id, MesExecutionPathNodeUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

