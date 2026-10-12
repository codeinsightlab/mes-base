package dev.mesbase.server;

import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.modules.directory.controller.BusinessDirectoryController;
import dev.mesbase.modules.directory.domain.DirectoryQuery;
import dev.mesbase.modules.directory.service.BusinessDirectoryService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.system.mapper.SysDeptMapper;
import dev.mesbase.system.mapper.SysPostMapper;
import dev.mesbase.system.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY H2 database using the real center Mapper/XML and Spring transaction manager.
 */
class BusinessDirectoryTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    private AnnotationConfigApplicationContext context;
    private BusinessDirectoryService service;
    private MockMvc http;
    private Set<String> grants;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        String schema = new ClassPathResource("db/system/V2__source_product_system.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String table : Set.of("sys_user", "sys_dept", "sys_post", "sys_role", "sys_user_role", "sys_menu")) {
            var pattern = Pattern.compile("create table " + table + "\\s*\\(.*?;", Pattern.DOTALL);
            var match = pattern.matcher(schema);
            if (!match.find()) throw new IllegalStateException("Missing source table " + table);
            jdbc.execute(match.group());
        }
        var catalog = new ResourceDatabasePopulator(new ClassPathResource(
                "db/system/V18__business_directory_read_permissions.sql"));
        catalog.execute(source);
        catalog.execute(source);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_type='F' AND perms LIKE 'business:directory:%:read'", Integer.class));
        jdbc.execute("ALTER TABLE sys_user ADD minio_url VARCHAR(500)");
        jdbc.execute("ALTER TABLE sys_user ADD wx_open_id VARCHAR(100)");
        jdbc.execute("ALTER TABLE sys_user ADD auth_revision BIGINT DEFAULT 0");
        jdbc.execute("ALTER TABLE sys_role ADD kind VARCHAR(16)");
        jdbc.execute("ALTER TABLE sys_role ADD factory_id VARCHAR(64)");
        jdbc.update("INSERT INTO sys_dept(dept_id,dept_name,parent_id,status) VALUES(10,'Department',0,'0'),(11,'Disabled',0,'1')");
        jdbc.update("INSERT INTO sys_post(post_id,post_code,post_name,post_sort,status) VALUES(20,'OP','Operator',0,'0'),(21,'OFF','Disabled',0,'1')");
        jdbc.update("INSERT INTO sys_user(user_id,user_name,nick_name,dept_id,password,email) VALUES(9007199254740993,'reference','Reference',10,'fixture-sensitive','private@example.invalid')");
        jdbc.update("INSERT INTO sys_user(user_id,user_name,nick_name,status,del_flag) VALUES(2,'disabled','Disabled','1','0'),(3,'deleted','Deleted','0','2')");
        var persistence = new PlatformPersistence(source, Set.of(SysUserMapper.class, SysDeptMapper.class, SysPostMapper.class), config -> {
            config.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            config.getTypeAliasRegistry().registerAlias("SysPost", SysPost.class);
            PersistenceFactories.pagination(config);
        });
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("fixture", Map.of("mes.system.enabled", "true")));
        context.register(Transactions.class);
        context.registerBean("masterTransactionManager", OwnedTransactionManager.class, persistence::transactionManager);
        context.registerBean(BusinessDirectoryService.class, () -> new BusinessDirectoryService(
                persistence.mapper(SysUserMapper.class), persistence.mapper(SysDeptMapper.class), persistence.mapper(SysPostMapper.class)));
        context.refresh();
        service = context.getBean(BusinessDirectoryService.class);
        var selection = mock(FactorySelectionService.class);
        when(selection.resolve(any(), any())).thenAnswer(call -> {
            Optional<FactoryId> selected = call.getArgument(1);
            return selected.map(FactoryScope::new);
        });
        grants = Set.of(BusinessDirectoryController.PERSONNEL_READ,
                BusinessDirectoryController.DEPARTMENT_READ, BusinessDirectoryController.POST_READ);
        var access = new FoundationAccessInterceptor(selection,
                (actor, scope, permission) -> grants.contains(permission.code()));
        http = MockMvcBuilders.standaloneSetup(new BusinessDirectoryController(service))
                .setControllerAdvice(new FoundationErrorAdvice()).addInterceptors(access)
                .addFilters((request, response, chain) -> {
                    FactoryContext.clear();
                    try {
                        chain.doFilter(request, response);
                    } finally {
                        FactoryContext.clear();
                    }
                }).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("business-reference-user", "", Set.of()));
    }

    @AfterEach
    void clean() {
        if (context != null) context.close();
        FactoryContext.clear();
        SecurityContextHolder.clearContext();
        PageHelper.clearPage();
    }

    @Test
    void businessGrantQueriesCenterWithoutPlatformRoleAndWhitelistProtectsUser() throws Exception {
        http.perform(get("/api/business/directory/personnel").header("X-Factory-Id", "F1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].roles").doesNotExist());
        http.perform(get("/api/business/directory/personnel/9007199254740993").header("X-Factory-Id", "F2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Reference"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertNull(PageHelper.getLocalPage());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void paginationSearchAndActiveReferencesUseRealXml() throws Exception {
        for (String resource : Set.of("departments", "posts")) {
            http.perform(get("/api/business/directory/" + resource).header("X-Factory-Id", "F1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        }
        http.perform(get("/api/business/directory/personnel").header("X-Factory-Id", "F1")
                .param("keyword", "absent")).andExpect(jsonPath("$.total").value(0));
        http.perform(get("/api/business/directory/personnel").header("X-Factory-Id", "F1")
                        .param("offset", "1").param("limit", "1"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items").isEmpty());
        for (String resource : Set.of("personnel/2", "personnel/3", "departments/11", "posts/21")) {
            http.perform(get("/api/business/directory/" + resource).header("X-Factory-Id", "F1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void permissionsAreIndependentAndMissingFactoryFails() throws Exception {
        grants = Set.of(BusinessDirectoryController.DEPARTMENT_READ);
        http.perform(get("/api/business/directory/personnel").header("X-Factory-Id", "F1"))
                .andExpect(status().isForbidden());
        http.perform(get("/api/business/directory/departments").header("X-Factory-Id", "F1"))
                .andExpect(status().isOk());
        http.perform(get("/api/business/directory/departments"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FACTORY_SELECTION_REQUIRED"));
    }

    @Test
    void onlyGetRoutesExistAndPagingBoundsRejectAbuse() throws Exception {
        for (String resource : Set.of("personnel", "departments", "posts")) {
            String url = "/api/business/directory/" + resource;
            http.perform(post(url)).andExpect(status().isMethodNotAllowed());
            http.perform(put(url + "/10")).andExpect(status().isMethodNotAllowed());
            http.perform(delete(url + "/10")).andExpect(status().isMethodNotAllowed());
            http.perform(get(url).header("X-Factory-Id", "F1").param("limit", "101"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(FoundationException.class,
                () -> service.posts(new DirectoryQuery(null, null, -1, 20))).code());
    }
}
