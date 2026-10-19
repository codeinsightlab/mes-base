package dev.mesbase.business.production;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.production.OperationUserDirectoryMapper;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;
import java.util.Set;
import java.util.UUID;
import java.sql.Connection;
import java.util.ArrayList;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TEST ONLY H2 center schema, real cross-table authorization projection and current menu path policy.
 */
class OperationUserDirectoryTest {
    OperationUserDirectory directory;
    JdbcTemplate jdbc;
    PlatformPersistence center;
    AnnotationConfigApplicationContext context;
    SqlBudget sqlBudget;

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class SqlBudget implements Interceptor {
        final List<String> statements = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            statements.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql().toLowerCase());
            return invocation.proceed();
        }

        long counts() {
            return statements.stream().filter(sql -> sql.matches("(?s).*select\\s+count\\s*\\(.*")).count();
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }


    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_menu(menu_id BIGINT PRIMARY KEY,menu_name VARCHAR(80),parent_id BIGINT,order_num INT,path VARCHAR(100),component VARCHAR(100),`query` VARCHAR(100),is_frame VARCHAR(1),is_cache VARCHAR(1),menu_type VARCHAR(1),visible VARCHAR(1),status VARCHAR(1),perms VARCHAR(100),icon VARCHAR(100),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user(user_id BIGINT PRIMARY KEY,user_name VARCHAR(80),nick_name VARCHAR(80),status VARCHAR(1),del_flag VARCHAR(1))");
        jdbc.execute("CREATE TABLE sys_role(role_id BIGINT PRIMARY KEY,kind VARCHAR(16),factory_id VARCHAR(64),status VARCHAR(1),del_flag VARCHAR(1))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
        jdbc.update("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,status,perms) VALUES(1,'Business',0,'production','M','0',NULL),(2,'Read',1,'#','F','0','production:read'),(3,'Platform',0,'system','M','0',NULL),(4,'Admin',3,'#','F','0','admin:read')");
        jdbc.update("INSERT INTO sys_role VALUES(1,'FACTORY','A','0','0'),(2,'FACTORY','B','0','0'),(3,'PLATFORM',NULL,'0','0'),(4,'PLATFORM',NULL,'0','0'),(5,'FACTORY','A','1','0')");
        jdbc.update("INSERT INTO sys_role_menu VALUES(1,2),(2,2),(3,2),(4,4),(5,2)");
        jdbc.update("INSERT INTO sys_user VALUES(1,'a','Factory A','0','0'),(2,'b','Factory B','0','0'),(3,'p','Platform business','0','0'),(4,'admin','Platform only','0','0'),(5,'off','Disabled user','1','0'),(6,'gone','Deleted user','0','2'),(7,'offrole','Disabled role','0','0'),(8,'empty','No grants','0','0')");
        jdbc.update("INSERT INTO sys_user_role VALUES(1,1),(2,2),(3,3),(4,4),(5,1),(6,1),(7,5)");
        sqlBudget = new SqlBudget();
        center = spy(new PlatformPersistence(source, Set.of(SysMenuMapper.class, OperationUserDirectoryMapper.class), c -> {
            c.getTypeAliasRegistry().registerAlias("SysMenu", SysMenu.class);
            PersistenceFactories.pagination(c);
            c.addInterceptor(sqlBudget);
        }));
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        context.register(Transactions.class);
        context.registerBean("masterTransactionManager", OwnedTransactionManager.class, center::transactionManager);
        context.registerBean(PlatformPersistence.class, () -> center);
        context.registerBean(SysMenuMapper.class, () -> center.mapper(SysMenuMapper.class));
        context.registerBean(OperationUserDirectoryMapper.class, () -> center.mapper(OperationUserDirectoryMapper.class));
        context.registerBean(OperationUserDirectory.class);
        context.refresh();
        directory = context.getBean(OperationUserDirectory.class);
        FactoryContext.bind(new FactoryScope(new FactoryId("A")));
    }

    @AfterEach
    void clear() {
        if (context != null) context.close();
        FactoryContext.clear();
        PageHelper.clearPage();
    }

    @Test
    void currentFactoryAndPlatformBusinessGrantsOnly() {
        assertEquals(List.of(1L, 3L), directory.page(null, 0, 20).items().stream()
                .map(OperationUserDirectoryMapper.UserOption::id).toList());
        FactoryContext.bind(new FactoryScope(new FactoryId("B")));
        assertEquals(List.of(2L, 3L), directory.page(null, 0, 20).items().stream()
                .map(OperationUserDirectoryMapper.UserOption::id).toList());
    }

    @Test
    void ordinaryPageUsesInjectedMappersWithoutIndependentWindow() {
        assertEquals(2, directory.page(null, 0, 20).total());
        directory.requireValid(1L);
        assertEquals(2, directory.validUsers(List.of(1L, 3L)).size());
        verify(center, never()).readOnly(any());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(2, directory.page(null, 0, 20).total());
    }

    @Test
    void validationRetainsIndependentWindowInsideBusinessTransaction() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var manager = new OwnedTransactionManager(source);
        new TransactionTemplate(manager).execute(status -> {
            Object connection = TransactionSynchronizationManager.getResource(source);
            directory.requireValid(1L);
            assertEquals(2, directory.validUsers(List.of(1L, 3L)).size());
            assertSame(connection, TransactionSynchronizationManager.getResource(source));
            assertThrows(FoundationException.class, () -> directory.page(null, 0, 20));
            return null;
        });
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(2, directory.page(null, 0, 20).total());
    }

    @Test
    void disabledDeletedForeignAndPlatformOnlyAreRejected() {
        for (var id : List.of(2L, 4L, 5L, 6L, 7L, 8L, 999L))
            assertThrows(FoundationException.class, () -> directory.requireValid(id));
        directory.requireValid(1L);
        directory.requireValid(3L);
    }

    @Test
    void permissionRevocationAndBulkLookupAreCurrent() {
        assertEquals(2, directory.validUsers(List.of(1L, 2L, 3L)).size());
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=1");
        assertThrows(FoundationException.class, () -> directory.requireValid(1L));
        assertEquals(1, directory.validUsers(List.of(1L, 2L, 3L)).size());
    }

    @Test
    void pageKeywordAndMissingScopeAreBoundedAndClean() {
        var page = directory.page(null, 0, 1);
        assertEquals(2, page.total());
        assertEquals(1, page.items().size());
        assertNull(PageHelper.getLocalPage());
        assertEquals(1, directory.page("Factory A", 0, 20).total());
        assertThrows(FoundationException.class, () -> directory.page(null, 0, 101));
        FactoryContext.clear();
        assertThrows(FoundationException.class, () -> directory.page(null, 0, 20));
    }

    @Test
    void internalEligibilitySkipsCountButPublicPageKeepsAccurateTotal() {
        directory.requireValid(1L);
        assertEquals(0, sqlBudget.counts());
        assertNull(PageHelper.getLocalPage());
        sqlBudget.statements.clear();
        assertEquals(2, directory.validUsers(List.of(1L, 2L, 3L)).size());
        assertEquals(0, sqlBudget.counts());
        assertNull(PageHelper.getLocalPage());
        sqlBudget.statements.clear();
        var page = directory.page(null, 0, 1);
        assertEquals(2, page.total());
        assertEquals(1, page.items().size());
        assertEquals(1, sqlBudget.counts());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void noMenuEmptyResultAndDatabaseFailureLeaveNoPaginationState() {
        assertEquals(List.of(), directory.validUsers(List.of(999L)));
        assertNull(PageHelper.getLocalPage());
        jdbc.update("UPDATE sys_menu SET status='1'");
        assertThrows(FoundationException.class, () -> directory.requireValid(1L));
        assertEquals(0, directory.page(null, 0, 20).total());
        assertNull(PageHelper.getLocalPage());
        jdbc.execute("DROP TABLE sys_menu");
        assertThrows(RuntimeException.class, () -> directory.requireValid(1L));
        assertNull(PageHelper.getLocalPage());
    }
}
