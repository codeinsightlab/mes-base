// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesExecutionAssignmentCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionAssignmentEntity;
import dev.mesbase.business.production.domain.MesExecutionAssignmentPage;
import dev.mesbase.business.production.domain.MesExecutionAssignmentQueryDto;
import dev.mesbase.business.production.domain.MesExecutionAssignmentUpdateCommand;
import java.util.List;

/**
 * 生产节点派工 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesExecutionAssignmentService {
    MesExecutionAssignmentPage selectPage(MesExecutionAssignmentQueryDto query);

    MesExecutionAssignmentEntity selectById(Long id);

    MesExecutionAssignmentEntity insert(MesExecutionAssignmentCreateCommand input);

    MesExecutionAssignmentEntity updateById(Long id, MesExecutionAssignmentUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

