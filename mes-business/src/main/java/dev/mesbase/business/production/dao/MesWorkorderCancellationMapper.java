// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesWorkorderCancellationEntity;
import dev.mesbase.business.production.domain.MesWorkorderCancellationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesWorkorderCancellationMapper {
    long countByExample(MesWorkorderCancellationExample example);

    int deleteByExample(MesWorkorderCancellationExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesWorkorderCancellationEntity row);

    int insertSelective(MesWorkorderCancellationEntity row);

    List<MesWorkorderCancellationEntity> selectByExample(MesWorkorderCancellationExample example);

    MesWorkorderCancellationEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesWorkorderCancellationEntity row,
                                 @Param("example") MesWorkorderCancellationExample example);

    int updateByExample(@Param("row") MesWorkorderCancellationEntity row,
                        @Param("example") MesWorkorderCancellationExample example);

    int updateByPrimaryKeySelective(MesWorkorderCancellationEntity row);

    int updateByPrimaryKey(MesWorkorderCancellationEntity row);
}