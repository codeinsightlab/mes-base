package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionPage;
import dev.mesbase.business.production.domain.MesExecutionCompletionQueryDto;

/**
 * 正式生产完工事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesExecutionCompletionService {
    MesExecutionCompletionPage selectPage(MesExecutionCompletionQueryDto query);

    MesExecutionCompletionEntity selectById(String id);


}

