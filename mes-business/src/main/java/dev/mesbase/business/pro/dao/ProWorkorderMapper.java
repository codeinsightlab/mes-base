// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.pro.dao;

import dev.mesbase.business.pro.domain.ProWorkorderEntity;
import dev.mesbase.business.pro.domain.ProWorkorderExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProWorkorderMapper {
    long countByExample(ProWorkorderExample example);

    int deleteByExample(ProWorkorderExample example);

    int deleteByPrimaryKey(Long workorderId);

    int insert(ProWorkorderEntity row);

    int insertSelective(ProWorkorderEntity row);

    List<ProWorkorderEntity> selectByExample(ProWorkorderExample example);

    ProWorkorderEntity selectByPrimaryKey(Long workorderId);

    int updateByExampleSelective(@Param("row") ProWorkorderEntity row, @Param("example") ProWorkorderExample example);

    int updateByExample(@Param("row") ProWorkorderEntity row, @Param("example") ProWorkorderExample example);

    int updateByPrimaryKeySelective(ProWorkorderEntity row);

    int updateByPrimaryKey(ProWorkorderEntity row);
}