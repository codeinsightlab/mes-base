package dev.mesbase.server;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.mapper.SysRoleMenuMapper;
import dev.mesbase.system.service.ISysMenuService;
import dev.mesbase.system.service.SystemPermissionService;
import dev.mesbase.system.service.impl.SysMenuServiceImpl;
import dev.mesbase.system.service.impl.SysRoleServiceImpl;
import dev.mesbase.web.controller.system.SysMenuController;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * TEST ONLY: real center Mapper/XML, H2 and Spring transactional proxies; no live menu changes.
 */
class MenuDeletionTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SysMenuMapper menus;
    private SysRoleMenuMapper bindings;
    private ISysMenuService service;
    private SystemPermissionService permissions;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        String schema = new ClassPathResource("db/system/V2__source_product_system.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String table : Set.of("sys_menu", "sys_role", "sys_role_menu", "sys_user", "sys_user_role")) {
            var match = Pattern.compile("create table " + table + "\\s*\\(.*?;", Pattern.DOTALL).matcher(schema);
            assertTrue(match.find(), table);
            jdbc.execute(match.group());
        }
        jdbc.execute("ALTER TABLE sys_role ADD kind VARCHAR(16) COMMENT '角色类型'");
        jdbc.execute("ALTER TABLE sys_role ADD factory_id VARCHAR(64) COMMENT '所属工厂编号'");
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,kind,factory_id,status) VALUES(7,'测试工厂角色','TEST_ONLY',1,'FACTORY','F1','0')");
        jdbc.update("INSERT INTO sys_user(user_id,user_name,nick_name) VALUES(1,'TEST_ONLY','测试用户')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(1,7)");
        jdbc.update("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type) VALUES(1,'业务',0,'pro','M')");
        jdbc.update("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,perms) VALUES(10,'待删除',1,'item','C','business:item:read'),(11,'保留',1,'other','C','business:other:read')");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(7,10),(7,11),(7,999)");
        var persistence = new PlatformPersistence(source, Set.of(SysMenuMapper.class, SysRoleMenuMapper.class), configuration -> {
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            configuration.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");
        });
        menus = persistence.mapper(SysMenuMapper.class);
        bindings = persistence.mapper(SysRoleMenuMapper.class);
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("fixture", Map.of("mes.system.enabled", true)));
        context.register(Transactions.class);
        context.registerBean("masterTransactionManager", OwnedTransactionManager.class, persistence::transactionManager);
        context.registerBean(SysMenuMapper.class, () -> menus);
        context.registerBean(SysRoleMenuMapper.class, () -> bindings);
        context.registerBean(SysRoleMapper.class, () -> mock(SysRoleMapper.class));
        context.registerBean(ManagedFactoryMapper.class, () -> mock(ManagedFactoryMapper.class));
        context.registerBean(Validator.class, () -> mock(Validator.class));
        context.registerBean(SystemPermissionService.class);
        context.registerBean(SysMenuServiceImpl.class);
        context.registerBean(SysMenuController.class);
        context.registerBean("TEST_ONLY_startupAudit", Object.class, () -> {
            context.getBean(SystemPermissionService.class).installEndpointScopes(Map.of("business:item:read", false));
            return new Object();
        });
        context.refresh();
        service = context.getBean(ISysMenuService.class);
        permissions = context.getBean(SystemPermissionService.class);
    }

    @AfterEach
    void cleanup() {
        if (context != null) context.close();
    }

    @Test
    void orphanRoleLinksDoNotBlockStartupAndDoNotGrantPermissions() {
        assertTrue(context.isActive());
        assertEquals(Set.of("business:item:read", "business:other:read"), permissions.permissions("TEST_ONLY", "F1"));
        assertTrue(permissions.menuPolicy().find(999L).isEmpty());
        assertThrows(ServiceException.class, () -> permissions.menuPolicy().require(999L));
    }

    @Test
    void deleteAssignedMenuCleansOnlyItsLinksThroughControllerAndSpringProxy() {
        assertTrue(AopUtils.isAopProxy(service));
        AjaxResult result = context.getBean(SysMenuController.class).remove(10L);
        assertEquals(200, result.get("code"));
        assertNull(menus.selectMenuById(10L));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=10"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=11"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role WHERE role_id=7"));
        assertEquals(Set.of("business:other:read"), permissions.permissions("TEST_ONLY", "F1"));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void auditFailureRollsBackBothMenuAndRoleLinksAndStillRejectsPlatformGrant() {
        jdbc.update("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type) VALUES(20,'平台',0,'system','M')");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(7,20)");
        assertThrows(ServiceException.class, () -> service.deleteMenuById(10L));
        assertEquals(10L, menus.selectMenuById(10L).getMenuId());
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=10"));
        assertThrows(ServiceException.class, permissions::validateMenuAuthorization);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void parentWithChildrenStillCannotBeDeleted() {
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(7,1)");
        assertThrows(ServiceException.class, () -> service.deleteMenuById(1L));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE menu_id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=1"));
    }

    @Test
    void explicitAssignmentOfDeletedMenuRemainsAnError() {
        var roles = new SysRoleServiceImpl();
        ReflectionTestUtils.setField(roles, "menus", menus);
        ReflectionTestUtils.setField(roles, "roleMenuMapper", bindings);
        var role = new SysRole();
        role.setRoleId(7L);
        role.setKind("FACTORY");
        role.setMenuIds(new Long[]{999L});
        assertThrows(ServiceException.class, () -> roles.insertRoleMenu(role));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=999"));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
