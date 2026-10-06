package dev.mesbase.system.factory;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import org.apache.ibatis.annotations.*;
import java.util.List;
@PersistenceOwner(PersistenceOwner.Kind.PLATFORM)
public interface ManagedFactoryMapper {
 @Select("SELECT "+COLUMNS+" FROM mf_factory ORDER BY factory_id") List<ManagedFactory> all();
 String COLUMNS="factory_id,enterprise_id,factory_code,factory_name,datasource_id,status,revision";
 @Select("SELECT "+COLUMNS+" FROM mf_factory ORDER BY factory_id LIMIT #{limit} OFFSET #{offset}") List<ManagedFactory> list(@Param("offset") int offset,@Param("limit") int limit);
 @Select("SELECT "+COLUMNS+" FROM mf_factory WHERE status='ENABLED' ORDER BY factory_id LIMIT #{limit} OFFSET #{offset}") List<ManagedFactory> enabled(@Param("offset") int offset,@Param("limit") int limit);
 @Select("SELECT "+COLUMNS+" FROM mf_factory WHERE factory_id=#{id}") ManagedFactory find(String id);
 @Insert("INSERT INTO mf_factory(factory_id,enterprise_id,factory_code,factory_name,datasource_id,status,revision) VALUES(#{factoryId},#{enterpriseId},#{factoryCode},#{factoryName},#{datasourceId},#{status},1)") int insert(ManagedFactory row);
 @Update("UPDATE mf_factory SET enterprise_id=#{enterpriseId},factory_code=#{factoryCode},factory_name=#{factoryName},datasource_id=#{datasourceId},status=#{status},revision=revision+1 WHERE factory_id=#{factoryId} AND revision=#{revision}") int update(ManagedFactory row);
 @Delete("DELETE FROM mf_factory WHERE factory_id=#{id} AND revision=#{revision}") int delete(@Param("id") String id,@Param("revision") long revision);
 @Select("SELECT count(*) FROM sys_role WHERE factory_id=#{id}") int roles(String id);
 @Select("SELECT count(*) FROM mes_master_sync WHERE factory_id=#{id}") int syncStates(String id);
 @Select("SELECT count(*) FROM sys_job WHERE factory_id=#{id}") int jobs(String id);
 @Select("<script>SELECT "+COLUMNS+" FROM mf_factory WHERE datasource_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>") List<ManagedFactory> byDatasources(@Param("ids") java.util.List<Long> ids);
 @Select("SELECT "+COLUMNS+" FROM mf_factory WHERE datasource_id=#{id}") ManagedFactory byDatasource(long id);
 @Select("SELECT count(*) FROM mf_factory WHERE datasource_id=#{id}") int bound(long id);
}
