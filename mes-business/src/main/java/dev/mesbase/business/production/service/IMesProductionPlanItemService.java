// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesProductionPlanItemCreateCommand;
import dev.mesbase.business.production.domain.MesProductionPlanItemEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemPage;
import dev.mesbase.business.production.domain.MesProductionPlanItemQueryDto;
import dev.mesbase.business.production.domain.MesProductionPlanItemUpdateCommand;
import java.util.List;

/**
 * 生产计划明细 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesProductionPlanItemService {
    MesProductionPlanItemPage selectPage(MesProductionPlanItemQueryDto query);

    MesProductionPlanItemEntity selectById(Long id);

    MesProductionPlanItemEntity insert(MesProductionPlanItemCreateCommand input);

    MesProductionPlanItemEntity updateById(Long id, MesProductionPlanItemUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

