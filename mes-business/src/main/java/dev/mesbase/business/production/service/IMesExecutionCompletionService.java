package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionQueryDto;

/**
 * 正式生产完工事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesExecutionCompletionService {
    Page<MesExecutionCompletionEntity> selectPage(MesExecutionCompletionQueryDto query);

    MesExecutionCompletionEntity selectById(String id);


}

