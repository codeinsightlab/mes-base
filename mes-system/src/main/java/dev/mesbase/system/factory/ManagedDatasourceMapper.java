package dev.mesbase.system.factory;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface ManagedDatasourceMapper {
 @Select("SELECT "+COLUMNS+" FROM mf_datasource ORDER BY datasource_id") List<ManagedDatasource> all();
 @Select("SELECT COUNT(*) FROM mf_datasource") long count();
 String COLUMNS="datasource_id,datasource_name,db_host,db_port,db_name,db_username,password_cipher,status,revision";
 @Select("SELECT "+COLUMNS+" FROM mf_datasource ORDER BY datasource_id LIMIT #{limit} OFFSET #{offset}") List<ManagedDatasource> list(@Param("offset") int offset,@Param("limit") int limit);
 @Select("<script>SELECT "+COLUMNS+" FROM mf_datasource WHERE datasource_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>") List<ManagedDatasource> byIds(@Param("ids") List<Long> ids);
 @Select("SELECT "+COLUMNS+" FROM mf_datasource WHERE datasource_id=#{id}") ManagedDatasource find(long id);
 @Select("SELECT "+COLUMNS+" FROM mf_datasource WHERE datasource_id=#{id} FOR UPDATE") ManagedDatasource lock(long id);
 @Select("SELECT LAST_INSERT_ID()") long insertedId();
 @Insert("INSERT INTO mf_datasource(datasource_name,db_host,db_port,db_name,db_username,password_cipher,status,revision) VALUES(#{datasourceName},#{dbHost},#{dbPort},#{dbName},#{dbUsername},#{passwordCipher},'DISABLED',1)") int insert(ManagedDatasource row);
 @Update("UPDATE mf_datasource SET datasource_name=#{datasourceName},db_host=#{dbHost},db_port=#{dbPort},db_name=#{dbName},db_username=#{dbUsername},password_cipher=#{passwordCipher},status=#{status},revision=revision+1 WHERE datasource_id=#{datasourceId} AND revision=#{revision}") int update(ManagedDatasource row);
 @Delete("DELETE FROM mf_datasource WHERE datasource_id=#{id} AND revision=#{revision}") int delete(@Param("id") long id,@Param("revision") long revision);
}
