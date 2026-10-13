// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.domain.EquipmentCreateCommand;
import dev.mesbase.business.basic.domain.EquipmentEntity;
import dev.mesbase.business.basic.domain.EquipmentPage;
import dev.mesbase.business.basic.domain.EquipmentQueryDto;
import dev.mesbase.business.basic.domain.EquipmentUpdateCommand;
import java.util.List;

/**
 * 设备 CRUD 用例。
 *
 * @author MES Base
 */
public interface IEquipmentService {
    EquipmentPage selectPage(EquipmentQueryDto query);

    EquipmentEntity selectById(Long id);

    EquipmentEntity insert(EquipmentCreateCommand input);

    EquipmentEntity updateById(Long id, EquipmentUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

