package dev.mesbase.system.mapper;
import org.apache.ibatis.annotations.Select;
public interface SystemBootstrapMapper {
 @Select("SELECT revision FROM platform_catalog_revision WHERE id=1 FOR UPDATE")long lock();
 @Select("SELECT COUNT(*) FROM sys_user")long users();
}
