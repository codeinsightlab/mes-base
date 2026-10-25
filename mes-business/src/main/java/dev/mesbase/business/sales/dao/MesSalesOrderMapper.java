// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.sales.dao;

import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesSalesOrderMapper {
    long countByExample(MesSalesOrderExample example);

    int deleteByExample(MesSalesOrderExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesSalesOrderEntity row);

    int insertSelective(MesSalesOrderEntity row);

    List<MesSalesOrderEntity> selectByExample(MesSalesOrderExample example);

    MesSalesOrderEntity selectByPrimaryKeyForUpdate(Long id);

    MesSalesOrderEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesSalesOrderEntity row, @Param("example") MesSalesOrderExample example);

    int updateByExample(@Param("row") MesSalesOrderEntity row, @Param("example") MesSalesOrderExample example);

    int updateByPrimaryKeySelective(MesSalesOrderEntity row);

    int updateByPrimaryKey(MesSalesOrderEntity row);
}