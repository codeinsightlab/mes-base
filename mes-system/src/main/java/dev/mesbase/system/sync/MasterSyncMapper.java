package dev.mesbase.system.sync;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

public interface MasterSyncMapper {
    String COLUMNS = "id,factory_id,sync_key,status,run_token,started_at,updated_at,finished_at,last_success_at,cursor_time,processed_count,error_message";

    @Insert("INSERT IGNORE INTO mes_master_sync(factory_id,sync_key) VALUES(#{factory},#{key})")
    int ensure(@Param("factory") String factory, @Param("key") String key);

    @Select("SELECT " + COLUMNS + " FROM mes_master_sync WHERE factory_id=#{factory} AND sync_key=#{key}")
    MasterSync find(@Param("factory") String factory, @Param("key") String key);

    @Update("UPDATE mes_master_sync SET status='RUNNING',run_token=#{token},started_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),finished_at=NULL,processed_count=0,error_message=NULL WHERE factory_id=#{factory} AND sync_key=#{key} AND status!='RUNNING'")
    int claim(@Param("factory") String factory, @Param("key") String key, @Param("token") String token);

    @Update("UPDATE mes_master_sync SET status='FAILED',finished_at=CURRENT_TIMESTAMP(6),error_message='同步租约过期，旧执行权已释放' WHERE factory_id=#{factory} AND sync_key=#{key} AND status='RUNNING' AND run_token=#{token} AND COALESCE(updated_at,started_at)<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL #{lease} SECOND)")
    int recover(@Param("factory") String factory, @Param("key") String key, @Param("token") String token, @Param("lease") long lease);

    @Update("UPDATE mes_master_sync SET processed_count=#{count},updated_at=CURRENT_TIMESTAMP(6) WHERE factory_id=#{factory} AND sync_key=#{key} AND run_token=#{token} AND status='RUNNING'")
    int progress(@Param("factory") String factory, @Param("key") String key, @Param("token") String token, @Param("count") long count);

    @Update("UPDATE mes_master_sync SET status=#{status},finished_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),last_success_at=CASE WHEN #{status}='SUCCEEDED' THEN CURRENT_TIMESTAMP(6) ELSE last_success_at END,cursor_time=CASE WHEN #{status}='SUCCEEDED' THEN #{cursor} ELSE cursor_time END,error_message=#{error} WHERE factory_id=#{factory} AND sync_key=#{key} AND run_token=#{token} AND status='RUNNING'")
    int finish(@Param("factory") String factory, @Param("key") String key, @Param("token") String token, @Param("status") String status, @Param("cursor") java.time.LocalDateTime cursor, @Param("error") String error);
}
