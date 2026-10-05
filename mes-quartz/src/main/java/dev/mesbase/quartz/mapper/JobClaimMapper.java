package dev.mesbase.quartz.mapper;
import org.apache.ibatis.annotations.*;
@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.PLATFORM)
public interface JobClaimMapper {
 @Update("UPDATE sys_job SET running_token=#{token},started_at=CURRENT_TIMESTAMP WHERE job_id=#{id} AND running_token IS NULL AND status='0'")int claim(@Param("id")Long id,@Param("token")String token);
 @Update("UPDATE sys_job SET running_token=NULL,started_at=NULL WHERE job_id=#{id} AND running_token=#{token}")int release(@Param("id")Long id,@Param("token")String token);
 @Insert("INSERT INTO sys_job_log(job_name,job_group,invoke_target,job_message,status,create_time,request_id,actor_id,factory_id,execution_scope,run_token,start_time,end_time,cost_time)VALUES(#{name},#{group},#{target},#{outcome},#{status},CURRENT_TIMESTAMP,#{requestId},#{actor},#{factory},#{scope},#{token},#{start},#{stop},#{cost})")void log(@Param("name")String name,@Param("group")String group,@Param("target")String target,@Param("outcome")String outcome,@Param("status")String status,@Param("requestId")String requestId,@Param("actor")String actor,@Param("factory")String factory,@Param("scope")String scope,@Param("token")String token,@Param("start")java.util.Date start,@Param("stop")java.util.Date stop,@Param("cost")long cost);
 @Update("UPDATE sys_job SET running_token=NULL,started_at=NULL WHERE job_id=#{id} AND status='1' AND running_token=#{token} AND started_at < DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 5 MINUTE)") int recover(@Param("id")Long id,@Param("token")String token);
 @Select("SELECT running_token AS runToken,started_at AS startedAt,status FROM sys_job WHERE job_id=#{id}")
 RunState state(@Param("id")Long id);
 record RunState(String runToken,java.util.Date startedAt,String status){}
}
