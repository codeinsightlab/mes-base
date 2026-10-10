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
import dev.mesbase.system.domain.WorkspaceRoleScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import dev.mesbase.system.service.impl.SysRoleServiceImpl;
import dev.mesbase.system.service.impl.SysMenuServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.times;


/**
 * TEST ONLY: real mapper SQL in isolated H2; no live role grants.
 */
class RoleScopePermissionTest {
    @Test
    void literalNullFactoryDoesNotCollideWithPlatformPermissionCacheKey() {
        var menus = mock(SysMenuMapper.class);
        var service = new SystemPermissionService(menus, mock(SysRoleMapper.class), mock(ManagedFactoryMapper.class));
        when(menus.selectMenuList(any())).thenReturn(List.of(menu(1L, 0L, "system", "system:role:list")));
        when(menus.selectScopedPermissions("fixture", null)).thenReturn(Set.of("system:role:list"));
        when(menus.selectScopedPermissions("fixture", "null")).thenReturn(Set.of("business:order:read"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            assertEquals(Set.of("system:role:list"), service.permissions("fixture", null));
            assertEquals(Set.of("business:order:read"), service.permissions("fixture", "null"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void authorizationIsSharedWithinRequestAndRequeriedForNextRequest() {
        var menus = mock(SysMenuMapper.class);
        var roles = mock(SysRoleMapper.class);
        var service = new SystemPermissionService(menus, roles, mock(ManagedFactoryMapper.class));
        when(menus.selectMenuList(any())).thenReturn(List.of(menu(1L, 0L, "pro", "business:order:read")));
        when(menus.selectScopedPermissions("fixture", "A")).thenReturn(Set.of("business:order:read"));
        var role = new SysRole();
        role.setKind("PLATFORM");
        role.setStatus("0");
        role.setDelFlag("0");
        var user = new SysUser();
        user.setUserName("fixture");
        user.setRoles(List.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(user, Set.of()), null, List.of()));
        try {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertTrue(service.hasPlatformRole("fixture"));
            assertTrue(service.grantedFactory("fixture", "A", "business:order:read"));
            when(menus.selectScopedPermissions("fixture", "A")).thenReturn(Set.of());
            assertTrue(service.grantedFactory("fixture", "A", "business:order:read"));
            service.menuCatalog();
            service.menuPolicy();
            service.menuPolicy();
            verify(menus, times(1)).selectScopedPermissions("fixture", "A");
            verify(menus, times(1)).selectMenuList(any());
            verify(roles, never()).selectWorkspaceScopes(any());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertFalse(service.grantedFactory("fixture", "A", "business:order:read"));
            verify(menus, times(2)).selectScopedPermissions("fixture", "A");
        } finally {
            RequestContextHolder.resetRequestAttributes();
            SecurityContextHolder.clearContext();
        }
    }

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
            sql.execute("INSERT INTO sys_user VALUES(1,'platform','0','0'),(2,'factory','0','0'),(3,'disabled','0','0'),(4,'platformOnly','0','0')");
            sql.execute("INSERT INTO sys_role VALUES(1,'PLATFORM',NULL,'0','0'),(2,'FACTORY','F1','0','0'),(3,'PLATFORM',NULL,'1','0'),(4,'PLATFORM',NULL,'0','0')");
            sql.execute("INSERT INTO sys_user_role VALUES(1,1),(2,2),(3,3),(4,4)");
            sql.execute("INSERT INTO sys_menu(menu_id,parent_id,path,menu_type,perms,status) VALUES "
                    + "(1,0,'system','M','','0'),(2,1,'role','C','system:role:list','0'),"
                    + "(3,0,'pro','M','','0'),(4,3,'order','C','business:order:read','0'),"
                    + "(5,1,'#','F','custom:platform:action','0'),(6,3,'other','C','business:other:read','0')");
            sql.execute("INSERT INTO sys_role_menu VALUES(1,3),(1,4),(1,2),(1,1),(2,3),(2,6),(2,2),(2,5),(3,4),(4,1),(4,2),(4,5)");
        }
        var persistence = new PlatformPersistence(source, Set.of(SysMenuMapper.class), configuration ->
                configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity"));
        var roles = mock(SysRoleMapper.class);
        when(roles.selectWorkspaceScopes("platform")).thenReturn(List.of(new WorkspaceRoleScope("PLATFORM", null)));
        when(roles.selectWorkspaceScopes("platformOnly")).thenReturn(List.of(new WorkspaceRoleScope("PLATFORM", null)));
        var service = new SystemPermissionService(persistence.mapper(SysMenuMapper.class),
                roles, mock(ManagedFactoryMapper.class));
        assertTrue(service.workspaces("platform").platformBusiness());
        assertEquals(List.of(), service.workspaces("platform").factoryIds());
        assertFalse(service.workspaces("platformOnly").platformBusiness());
        assertFalse(service.workspaces("disabled").platformBusiness());
        assertEquals(Set.of("system:role:list", "business:order:read"), service.permissions("platform", null));
        assertEquals(Set.of("business:order:read"), service.permissions("platform", "F1"));
        assertEquals(Set.of("business:order:read"), service.permissions("platform", "F2"));
        assertEquals(Set.of("business:other:read"), service.permissions("factory", "F1"));
        assertEquals(Set.of(), service.permissions("factory", "F2"));
        assertEquals(Set.of(), service.permissions("factory", null));
        assertEquals(Set.of(), service.permissions("disabled", "F1"));
        assertTrue(service.grantedPlatform("platform", "system:role:list"));
        assertTrue(service.grantedPlatform("platform", "business:order:read"));
        assertFalse(service.grantedPlatform("factory", "system:role:list"));
        assertFalse(service.grantedPlatform("disabled", "system:role:list"));
        assertFalse(service.grantedFactory("platform", "F1", "system:role:list"));
        assertFalse(service.grantedFactory("platform", "", "business:order:read"));
        assertFalse(service.grantedFactory("platform", null, "business:order:read"));
        assertAuthorizationChain(service);
        var navigation = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(navigation, "menuMapper", persistence.mapper(SysMenuMapper.class));
        ReflectionTestUtils.setField(navigation, "permissionService", service);
        assertEquals(List.of("pro"), navigation.selectFactoryMenuTree("platform", "F1").stream()
                .map(SysMenu::getPath).toList());
        var catalogPermissions = mock(SystemPermissionService.class);
        when(catalogPermissions.menuPolicy()).thenReturn(service.menuPolicy());
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

    private static void assertAuthorizationChain(SystemPermissionService service) throws Exception {
        var selection = mock(FactorySelectionService.class);
        when(selection.resolve(any(), any())).thenAnswer(invocation -> {
            Optional<FactoryId> requested = invocation.getArgument(1);
            return requested.map(FactoryScope::new);
        });
        var configuration = new SystemConfiguration();
        var interceptor = new FoundationAccessInterceptor(selection,
                configuration.factoryAuthorization(service), configuration.platformAuthorization(service));
        var endpoints = new PermissionEndpoints();
        var platform = new HandlerMethod(endpoints, PermissionEndpoints.class.getMethod("platform"));
        var business = new HandlerMethod(endpoints, PermissionEndpoints.class.getMethod("business"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("platform", "TEST ONLY", List.of()));
        try {
            var request = new MockHttpServletRequest("GET", "/api/system/role");
            var response = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(request, response, platform));
            request.setRequestURI("/api/pro/order");
            assertThrows(FoundationException.class, () -> interceptor.preHandle(request, response, business));
            request.addHeader("X-Factory-Id", "F1");
            request.setRequestURI("/api/system/role");
            assertTrue(interceptor.preHandle(request, response, platform));
            request.setRequestURI("/api/pro/order");
            assertTrue(interceptor.preHandle(request, response, business));
            FactoryContext.clear();
            request.removeHeader("X-Factory-Id");
            request.addHeader("X-Factory-Id", "F2");
            assertTrue(interceptor.preHandle(request, response, business));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("factory", "TEST ONLY", List.of()));
            request.setRequestURI("/api/system/role");
            assertThrows(FoundationException.class, () -> interceptor.preHandle(request, response, platform));
        } finally {
            FactoryContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    public static class PermissionEndpoints {
        @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:role:list",
                permissionScope = EndpointAccess.PermissionScope.PLATFORM)
        public void platform() {
        }

        @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:order:read")
        public void business() {
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
