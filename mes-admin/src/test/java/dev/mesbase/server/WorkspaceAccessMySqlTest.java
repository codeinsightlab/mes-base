package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.service.SystemPermissionService;
import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Explicit opt-in, read-only real MySQL/Druid regression; no role or schema changes. */
class WorkspaceAccessMySqlTest {
    @Test void currentMixedCollationSchemaWorksThroughProductionMapperAndWallFilter() throws Exception {
        String file=System.getProperty("mes.test.workspace.config");
        Assumptions.assumeTrue(file!=null,"Requires explicit local development configuration");
        var properties=new Properties();
        for(var source:new YamlPropertySourceLoader().load("private",new FileSystemResource(Path.of(file)))) {
            var values=(EnumerablePropertySource<?>)source;
            for(String key:values.getPropertyNames())properties.setProperty(key,String.valueOf(values.getProperty(key)));
        }
        var url=properties.getProperty("mes.platform.url");var uri=URI.create(url.substring(5));
        if(uri.getHost()==null||!"/mes_base_dev".equals(uri.getPath()))throw new IllegalArgumentException("Explicit development database required");
        var settings=new DruidPools.Settings(0,0,2,5000,5000,30000,60000,300000,900000,"SELECT 1",true,false,false,"stat,wall");
        try(var pool=DruidPools.create(settings,"TEST_ONLY_workspace_read",url,properties.getProperty("mes.platform.username"),properties.getProperty("mes.platform.password"))) {
            var persistence=new PlatformPersistence(pool,Set.of(SysRoleMapper.class,ManagedFactoryMapper.class),configuration->configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity"));
            var roles=persistence.mapper(SysRoleMapper.class);var factories=persistence.mapper(ManagedFactoryMapper.class);
            var service=new SystemPermissionService(mock(SysMenuMapper.class),roles,factories);
            assertTrue(service.workspaces("admin").platform());
            assertTrue(service.workspaces("TEST_ONLY_NONEXISTENT_USER").factoryIds().isEmpty());
            // Bound-value lookup exercises the second query even when the real admin is platform-only.
            assertTrue(factories.enabledIds(List.of("TEST_ONLY_NONEXISTENT_FACTORY")).isEmpty());
        }
    }
}
