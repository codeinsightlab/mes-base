package dev.mesbase.business.basic.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.basic.domain.EquipmentCreateCommand;
import dev.mesbase.business.basic.domain.EquipmentEntity;
import dev.mesbase.business.basic.domain.EquipmentQueryDto;
import dev.mesbase.business.basic.domain.EquipmentUpdateCommand;
import java.util.List;

/**
 * 设备 CRUD 用例。
 *
 * @author MES Base
 */
public interface IEquipmentService {
    Page<EquipmentEntity> selectPage(EquipmentQueryDto query);

    EquipmentEntity selectById(Long id);

    EquipmentEntity insert(EquipmentCreateCommand input);

    EquipmentEntity updateById(Long id, EquipmentUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

