// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.acceptance.dao;

import dev.mesbase.modules.acceptance.domain.PersonEntity;
import dev.mesbase.modules.acceptance.domain.PersonExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PersonMapper {
    long countByExample(PersonExample example);

    int deleteByExample(PersonExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PersonEntity row);

    int insertSelective(PersonEntity row);

    List<PersonEntity> selectByExample(PersonExample example);

    PersonEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") PersonEntity row, @Param("example") PersonExample example);

    int updateByExample(@Param("row") PersonEntity row, @Param("example") PersonExample example);

    int updateByPrimaryKeySelective(PersonEntity row);

    int updateByPrimaryKey(PersonEntity row);
}