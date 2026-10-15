// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesOperationUserEntity;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesOperationUserMapper {
    long countByExample(MesOperationUserExample example);

    int deleteByExample(MesOperationUserExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesOperationUserEntity row);

    int insertSelective(MesOperationUserEntity row);

    List<MesOperationUserEntity> selectByExample(MesOperationUserExample example);

    MesOperationUserEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesOperationUserEntity row,
                                 @Param("example") MesOperationUserExample example);

    int updateByExample(@Param("row") MesOperationUserEntity row, @Param("example") MesOperationUserExample example);

    int updateByPrimaryKeySelective(MesOperationUserEntity row);

    int updateByPrimaryKey(MesOperationUserEntity row);
}