package dev.mesbase.server;

import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.mapper.SysRoleMenuMapper;
import dev.mesbase.system.service.SystemPermissionService;
import dev.mesbase.system.service.impl.SysRoleServiceImpl;
import dev.mesbase.system.service.impl.SysMenuServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY: real mapper SQL in isolated H2; no live role grants.
 */
class RoleScopePermissionTest {
    @Test
    void platformBusinessGrantsApplyToSelectedFactoriesWithoutFactoryRoleEscalation() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        String migration = Files.readString(Path.of("mes-admin/src/main/resources/db/system/V2__source_product_system.sql"));
        int start = migration.indexOf("create table sys_menu (");
        String menuDdl = migration.substring(start, migration.indexOf(';', start)).replaceAll("\\) engine=.*", ")");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute(menuDdl);
            sql.execute("ALTER TABLE sys_menu ALTER COLUMN menu_name SET DEFAULT 'TEST ONLY'");
            sql.execute("CREATE TABLE sys_user(user_id BIGINT,user_name VARCHAR(30),status VARCHAR(1),del_flag VARCHAR(1))");
            sql.execute("CREATE TABLE sys_role(role_id BIGINT,kind VARCHAR(10),factory_id VARCHAR(30),status VARCHAR(1),del_flag VARCHAR(1))");
            sql.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
            sql.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
            sql.execute("INSERT INTO sys_user VALUES(1,'platform','0','0'),(2,'factory','0','0'),(3,'disabled','0','0')");
            sql.execute("INSERT INTO sys_role VALUES(1,'PLATFORM',NULL,'0','0'),(2,'FACTORY','F1','0','0'),(3,'PLATFORM',NULL,'1','0')");
            sql.execute("INSERT INTO sys_user_role VALUES(1,1),(2,2),(3,3)");
            sql.execute("INSERT INTO sys_menu(menu_id,parent_id,path,menu_type,perms,status) VALUES "
                    + "(1,0,'system','M','','0'),(2,1,'role','C','system:role:list','0'),"
                    + "(3,0,'pro','M','','0'),(4,3,'order','C','business:order:read','0'),"
                    + "(5,1,'#','F','custom:platform:action','0'),(6,3,'other','C','business:other:read','0')");
            sql.execute("INSERT INTO sys_role_menu VALUES(1,3),(1,4),(1,2),(1,1),(2,3),(2,6),(2,2),(2,5),(3,4)");
        }
        var persistence = new PlatformPersistence(source, Set.of(SysMenuMapper.class), configuration ->
                configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity"));
        var service = new SystemPermissionService(persistence.mapper(SysMenuMapper.class),
                mock(SysRoleMapper.class), mock(ManagedFactoryMapper.class));
        assertEquals(Set.of("system:role:list", "business:order:read"), service.permissions("platform", null));
        assertEquals(Set.of("business:order:read"), service.permissions("platform", "F1"));
        assertEquals(Set.of("business:order:read"), service.permissions("platform", "F2"));
        assertEquals(Set.of("business:other:read"), service.permissions("factory", "F1"));
        assertEquals(Set.of(), service.permissions("factory", "F2"));
        assertEquals(Set.of(), service.permissions("factory", null));
        assertEquals(Set.of(), service.permissions("disabled", "F1"));
        assertFalse(service.granted("platform", null, "business:order:read"));
        var navigation = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(navigation, "menuMapper", persistence.mapper(SysMenuMapper.class));
        assertEquals(List.of("pro"), navigation.selectFactoryMenuTree("platform", "F1").stream()
                .map(SysMenu::getPath).toList());
        var catalogPermissions = mock(SystemPermissionService.class);
        when(catalogPermissions.permissions("fixture", null)).thenReturn(Set.of("system:role:add"));
        ReflectionTestUtils.setField(navigation, "permissionService", catalogPermissions);
        var user = new SysUser();
        user.setUserName("fixture");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(user, Set.of()), "TEST ONLY"));
        try {
            assertEquals(3, navigation.selectAssignableMenus(new SysMenu(), 1L, "FACTORY").size());
            assertEquals(6, navigation.selectAssignableMenus(new SysMenu(), 1L, "PLATFORM").size());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void factoryAssignmentRejectsPlatformDescendantsAndPlatformAssignmentAcceptsBoth() {
        var mapper = mock(SysMenuMapper.class);
        var bindings = mock(SysRoleMenuMapper.class);
        SysMenu platform = menu(1L, 0L, "monitor", "");
        SysMenu action = menu(2L, 1L, "#", "custom:server:read");
        SysMenu business = menu(3L, 0L, "pro", "business:order:read");
        when(mapper.selectMenuList(any())).thenReturn(List.of(platform, action, business));
        var service = new SysRoleServiceImpl();
        ReflectionTestUtils.setField(service, "menus", mapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", bindings);
        var role = new SysRole();
        role.setKind("FACTORY");
        role.setRoleId(42L);
        role.setMenuIds(new Long[]{2L});
        assertThrows(ServiceException.class, () -> service.insertRoleMenu(role));
        verify(bindings, never()).batchRoleMenu(any());
        role.setKind("PLATFORM");
        role.setMenuIds(new Long[]{2L, 3L});
        service.insertRoleMenu(role);
        verify(bindings).batchRoleMenu(any());
    }

    private static SysMenu menu(Long id, Long parent, String path, String permission) {
        var menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parent);
        menu.setPath(path);
        menu.setPerms(permission);
        return menu;
    }
}
