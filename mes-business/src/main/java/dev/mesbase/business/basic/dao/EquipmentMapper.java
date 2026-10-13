// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.EquipmentEntity;
import dev.mesbase.business.basic.domain.EquipmentExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface EquipmentMapper {
    long countByExample(EquipmentExample example);

    int deleteByExample(EquipmentExample example);

    int deleteByPrimaryKey(Long id);

    int insert(EquipmentEntity row);

    int insertSelective(EquipmentEntity row);

    List<EquipmentEntity> selectByExample(EquipmentExample example);

    EquipmentEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") EquipmentEntity row, @Param("example") EquipmentExample example);

    int updateByExample(@Param("row") EquipmentEntity row, @Param("example") EquipmentExample example);

    int updateByPrimaryKeySelective(EquipmentEntity row);

    int updateByPrimaryKey(EquipmentEntity row);
}