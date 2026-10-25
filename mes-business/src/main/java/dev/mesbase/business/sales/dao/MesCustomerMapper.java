// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.sales.dao;

import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesCustomerExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesCustomerMapper {
    long countByExample(MesCustomerExample example);

    int deleteByExample(MesCustomerExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesCustomerEntity row);

    int insertSelective(MesCustomerEntity row);

    List<MesCustomerEntity> selectByExample(MesCustomerExample example);

    MesCustomerEntity selectByPrimaryKeyForUpdate(Long id);

    MesCustomerEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesCustomerEntity row, @Param("example") MesCustomerExample example);

    int updateByExample(@Param("row") MesCustomerEntity row, @Param("example") MesCustomerExample example);

    int updateByPrimaryKeySelective(MesCustomerEntity row);

    int updateByPrimaryKey(MesCustomerEntity row);
}