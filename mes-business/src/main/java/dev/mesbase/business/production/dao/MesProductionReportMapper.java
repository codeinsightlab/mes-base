package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface MesProductionReportMapper {
    MesProductionReportEntity selectByRequestId(@Param("requestId") String requestId);

    long countByExample(MesProductionReportExample example);

    int deleteByExample(MesProductionReportExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionReportEntity row);

    @Insert({"<script>INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,",
            "reference_report_id,operator_user_id,recorded_by,report_time,workstation_id,reason,create_time,request_id,command_fingerprint) VALUES ",
            "<foreach collection='rows' item='r' separator=','>",
            "(#{r.executionId},#{r.routingNodeId},#{r.recordType},#{r.quantityDelta},#{r.referenceReportId},",
            "#{r.operatorUserId},#{r.recordedBy},#{r.reportTime},#{r.workstationId},#{r.reason},#{r.createTime},",
            "#{r.requestId},#{r.commandFingerprint})</foreach></script>"})
    @Options(useGeneratedKeys = true, keyProperty = "rows.id", keyColumn = "id")
    int insertBatch(@Param("rows") List<MesProductionReportEntity> rows);

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
