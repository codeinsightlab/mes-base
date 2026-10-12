// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.service;

import dev.mesbase.modules.material.domain.WorkstationCreateCommand;
import dev.mesbase.modules.material.domain.WorkstationEntity;
import dev.mesbase.modules.material.domain.WorkstationPage;
import dev.mesbase.modules.material.domain.WorkstationQueryDto;
import dev.mesbase.modules.material.domain.WorkstationUpdateCommand;
import java.util.List;

/**
 * 工作站 CRUD 用例。
 *
 * @author MES Base
 */
public interface IWorkstationService {
    WorkstationPage selectPage(WorkstationQueryDto query);

    WorkstationEntity selectById(Long id);

    WorkstationEntity insert(WorkstationCreateCommand input);

    WorkstationEntity updateById(Long id, WorkstationUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

