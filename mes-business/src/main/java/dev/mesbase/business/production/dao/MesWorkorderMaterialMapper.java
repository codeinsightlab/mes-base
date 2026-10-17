// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesWorkorderMaterialMapper {
    long countByExample(MesWorkorderMaterialExample example);

    int deleteByExample(MesWorkorderMaterialExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesWorkorderMaterialEntity row);

    int insertSelective(MesWorkorderMaterialEntity row);

    List<MesWorkorderMaterialEntity> selectByExample(MesWorkorderMaterialExample example);

    MesWorkorderMaterialEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesWorkorderMaterialEntity row,
                                 @Param("example") MesWorkorderMaterialExample example);

    int updateByExample(@Param("row") MesWorkorderMaterialEntity row,
                        @Param("example") MesWorkorderMaterialExample example);

    int updateByPrimaryKeySelective(MesWorkorderMaterialEntity row);

    int updateByPrimaryKey(MesWorkorderMaterialEntity row);
}