// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.resource.dao;

import dev.mesbase.business.resource.domain.PersonnelEntity;
import dev.mesbase.business.resource.domain.PersonnelExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PersonnelMapper {
    long countByExample(PersonnelExample example);

    int deleteByExample(PersonnelExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PersonnelEntity row);

    int insertSelective(PersonnelEntity row);

    List<PersonnelEntity> selectByExample(PersonnelExample example);

    PersonnelEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") PersonnelEntity row, @Param("example") PersonnelExample example);

    int updateByExample(@Param("row") PersonnelEntity row, @Param("example") PersonnelExample example);

    int updateByPrimaryKeySelective(PersonnelEntity row);

    int updateByPrimaryKey(PersonnelEntity row);
}