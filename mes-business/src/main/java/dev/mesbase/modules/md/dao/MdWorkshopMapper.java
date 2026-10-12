// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.md.dao;

import dev.mesbase.modules.md.domain.MdWorkshopEntity;
import dev.mesbase.modules.md.domain.MdWorkshopExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MdWorkshopMapper {
    long countByExample(MdWorkshopExample example);

    int deleteByExample(MdWorkshopExample example);

    int deleteByPrimaryKey(Long workshopId);

    int insert(MdWorkshopEntity row);

    int insertSelective(MdWorkshopEntity row);

    List<MdWorkshopEntity> selectByExample(MdWorkshopExample example);

    MdWorkshopEntity selectByPrimaryKey(Long workshopId);

    int updateByExampleSelective(@Param("row") MdWorkshopEntity row, @Param("example") MdWorkshopExample example);

    int updateByExample(@Param("row") MdWorkshopEntity row, @Param("example") MdWorkshopExample example);

    int updateByPrimaryKeySelective(MdWorkshopEntity row);

    int updateByPrimaryKey(MdWorkshopEntity row);
}