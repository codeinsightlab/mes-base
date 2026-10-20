package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

public interface MesExecutionPathNodeMapper {
    long countByExample(MesExecutionPathNodeExample example);

    int deleteByExample(MesExecutionPathNodeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesExecutionPathNodeEntity row);

    @Insert({"<script>INSERT INTO mes_execution_path_node(execution_id,routing_node_id,sequence_no) VALUES ",
            "<foreach collection='rows' item='r' separator=','>",
            "(#{r.executionId},#{r.routingNodeId},#{r.sequenceNo})</foreach></script>"})
    int insertBatch(@Param("rows") List<MesExecutionPathNodeEntity> rows);

    int insertSelective(MesExecutionPathNodeEntity row);

    List<MesExecutionPathNodeEntity> selectByExample(MesExecutionPathNodeExample example);

    MesExecutionPathNodeEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesExecutionPathNodeEntity row,
                                 @Param("example") MesExecutionPathNodeExample example);

    int updateByExample(@Param("row") MesExecutionPathNodeEntity row,
                        @Param("example") MesExecutionPathNodeExample example);

    int updateByPrimaryKeySelective(MesExecutionPathNodeEntity row);

    int updateByPrimaryKey(MesExecutionPathNodeEntity row);
}
