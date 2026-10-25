// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesProductionPlanCreateCommand;
import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.business.production.domain.MesProductionPlanPage;
import dev.mesbase.business.production.domain.MesProductionPlanQueryDto;
import dev.mesbase.business.production.domain.MesProductionPlanUpdateCommand;
import java.util.List;

/**
 * 生产计划 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesProductionPlanService {
    MesProductionPlanPage selectPage(MesProductionPlanQueryDto query);

    MesProductionPlanEntity selectById(Long id);

    MesProductionPlanEntity insert(MesProductionPlanCreateCommand input);

    MesProductionPlanEntity updateById(Long id, MesProductionPlanUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

