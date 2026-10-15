// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionReportMapper {
    long countByExample(MesProductionReportExample example);

    int deleteByExample(MesProductionReportExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionReportEntity row);

    int insertSelective(MesProductionReportEntity row);

    List<MesProductionReportEntity> selectByExample(MesProductionReportExample example);

    MesProductionReportEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesProductionReportEntity row,
                                 @Param("example") MesProductionReportExample example);

    int updateByExample(@Param("row") MesProductionReportEntity row,
                        @Param("example") MesProductionReportExample example);

    int updateByPrimaryKeySelective(MesProductionReportEntity row);

    int updateByPrimaryKey(MesProductionReportEntity row);
}