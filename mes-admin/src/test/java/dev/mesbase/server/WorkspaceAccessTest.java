package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.service.SystemPermissionService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** TEST ONLY role/scope contract, independent of enabled-factory selection admission. */
class WorkspaceAccessTest {
    @Test void scopesComeFromEnabledRolesAndFactoriesRatherThanRoleNamesOrFactoryAvailability() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("CREATE TABLE sys_user(user_id BIGINT,user_name VARCHAR(30),status VARCHAR(1),del_flag VARCHAR(1))");
            s.execute("CREATE TABLE sys_role(role_id BIGINT,kind VARCHAR(10),factory_id VARCHAR(30),status VARCHAR(1),del_flag VARCHAR(1))");
            s.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
            s.execute("CREATE TABLE mf_factory(factory_id VARCHAR(30),status VARCHAR(10))");
            s.execute("INSERT INTO sys_user VALUES(1,'dual','0','0'),(2,'platform','0','0'),(3,'factory','0','0'),(4,'empty','0','0'),(5,'disabled','1','0')");
            s.execute("INSERT INTO mf_factory VALUES('F','ENABLED'),('OFF','DISABLED')");
            s.execute("INSERT INTO sys_role VALUES(1,'PLATFORM',NULL,'0','0'),(2,'FACTORY','F','0','0'),(3,'FACTORY','OFF','0','0'),(4,'FACTORY','F','1','0'),(5,'FACTORY','F','0','2')");
            s.execute("INSERT INTO sys_user_role VALUES(1,1),(1,2),(1,3),(1,4),(1,5),(2,1),(3,2),(5,1)");
        }
        var persistence=new PlatformPersistence(source,Set.of(SysRoleMapper.class,ManagedFactoryMapper.class),configuration->configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity"));
        var service=new SystemPermissionService(mock(SysMenuMapper.class),persistence.mapper(SysRoleMapper.class),persistence.mapper(ManagedFactoryMapper.class));
        assertTrue(service.workspaces("dual").platform());assertEquals(List.of("F"),service.workspaces("dual").factoryIds());
        assertTrue(service.workspaces("platform").platform());assertTrue(service.workspaces("platform").factoryIds().isEmpty());
        assertFalse(service.workspaces("factory").platform());assertEquals(List.of("F"),service.workspaces("factory").factoryIds());
        assertEquals(new SystemPermissionService.WorkspaceAccess(false,List.of()),service.workspaces("empty"));
        assertEquals(new SystemPermissionService.WorkspaceAccess(false,List.of()),service.workspaces("disabled"));
    }
}
