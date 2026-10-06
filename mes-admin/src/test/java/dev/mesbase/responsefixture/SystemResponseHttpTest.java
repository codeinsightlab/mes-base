package dev.mesbase.responsefixture;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.framework.web.service.SysLoginService;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.framework.websocket.MessageEventHub;
import dev.mesbase.system.domain.SysAttachment;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.domain.SysLogininfor;
import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.domain.SysNotice;
import dev.mesbase.system.domain.SysOperLog;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.system.service.IAutoCodePartService;
import dev.mesbase.system.service.IAutoCodeRuleService;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.ISysDeptService;
import dev.mesbase.system.service.ISysDictDataService;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.system.service.ISysLogininforService;
import dev.mesbase.system.service.ISysMenuService;
import dev.mesbase.system.service.ISysMessageService;
import dev.mesbase.system.service.ISysNoticeService;
import dev.mesbase.system.service.ISysOperLogService;
import dev.mesbase.system.service.ISysPostService;
import dev.mesbase.system.service.ISysRoleService;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.system.service.SystemPermissionService;
import dev.mesbase.system.service.impl.SysAttachmentServiceImpl;
import dev.mesbase.system.service.impl.SysMessageServiceImpl;
import dev.mesbase.system.service.impl.SysProfileService;
import dev.mesbase.web.controller.monitor.SysLogininforController;
import dev.mesbase.web.controller.monitor.SysOperlogController;
import dev.mesbase.web.controller.system.SysAutoCodePartController;
import dev.mesbase.web.controller.system.SysAutoCodeRuleController;
import dev.mesbase.web.controller.system.SysConfigController;
import dev.mesbase.web.controller.system.SysDeptController;
import dev.mesbase.web.controller.system.SysDictDataController;
import dev.mesbase.web.controller.system.SysDictTypeController;
import dev.mesbase.web.controller.system.SysMenuController;
import dev.mesbase.web.controller.system.SysMessageController;
import dev.mesbase.web.controller.system.SysNoticeController;
import dev.mesbase.web.controller.system.SysPostController;
import dev.mesbase.web.controller.system.SysRoleController;
import dev.mesbase.web.controller.system.SysUserController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import dev.mesbase.common.core.domain.TreeSelect;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.web.controller.system.SystemFileController;
import dev.mesbase.web.controller.monitor.CacheController;
import dev.mesbase.web.controller.system.SystemInboxController;
import dev.mesbase.web.controller.system.SystemSessionController;
import dev.mesbase.web.controller.system.SysProfileController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Standalone MVC fixture: real Controllers and serialization, mocked services, no response Advice. */
class SystemResponseHttpTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    static Stream<Arguments> routes() {
        return Stream.of(Arguments.of("system/user/list", "/rows/0/userId"),
                Arguments.of("system/user/1", "/data/userId"),
                Arguments.of("system/role/list", "/rows/0/roleId"),
                Arguments.of("system/role/1", "/data/roleId"),
                Arguments.of("system/post/list", "/rows/0/postId"),
                Arguments.of("system/post/1", "/data/postId"),
                Arguments.of("system/dict/data/list", "/rows/0/dictCode"),
                Arguments.of("system/dict/data/1", "/data/dictCode"),
                Arguments.of("system/dict/type/list", "/rows/0/dictId"),
                Arguments.of("system/dict/type/1", "/data/dictId"),
                Arguments.of("system/config/list", "/rows/0/configId"),
                Arguments.of("system/config/1", "/data/configId"),
                Arguments.of("system/notice/list", "/rows/0/noticeId"),
                Arguments.of("system/notice/1", "/data/noticeId"),
                Arguments.of("system/autocode/rule/list", "/rows/0/ruleId"),
                Arguments.of("system/autocode/rule/1", "/data/ruleId"),
                Arguments.of("system/autocode/part/list", "/rows/0/partId"),
                Arguments.of("system/autocode/part/1", "/data/partId"),
                Arguments.of("system/message/list", "/rows/0/messageId"),
                Arguments.of("system/message/1", "/data/messageId"),
                Arguments.of("system/post/optionselect", "/data/0/postId"),
                Arguments.of("system/role/optionselect", "/data/0/roleId"),
                Arguments.of("system/dict/type/optionselect", "/data/0/dictId"),
                Arguments.of("system/dept/list", "/data/0/deptId"),
                Arguments.of("system/dept/list/exclude/2", "/data/0/deptId"),
                Arguments.of("system/dept/1", "/data/deptId"),
                Arguments.of("system/dept/treeselect", "/data/0/id"),
                Arguments.of("system/dept/roleDeptTreeselect/1", "/checkedKeys/0"),
                Arguments.of("system/menu/list", "/data/0/menuId"),
                Arguments.of("system/menu/1", "/data/menuId"),
                Arguments.of("system/menu/treeselect", "/data/0/id"),
                Arguments.of("system/menu/roleMenuTreeselect/1", "/checkedKeys/0"),
                Arguments.of("system/user/authRole/1", "/user/userId"),
                Arguments.of("system/role/authUser/allocatedList", "/rows/0/userId"),
                Arguments.of("system/role/authUser/unallocatedList", "/rows/0/userId"),
                Arguments.of("system/dict/data/type/fixture", "/data/0/dictCode"),
                Arguments.of("system/inbox", "/rows/0/messageId"),
                Arguments.of("mobile/system/message/getMyMessage", "/rows/0/messageId"),
                Arguments.of("platform/files?resource=avatar", "/rows/0/attachmentId"),
                Arguments.of("platform/files/fixture?resource=avatar", "/attachmentId"),
                Arguments.of("system/user/profile", "/data/userId"),
                Arguments.of("system/info", "/user/userId"),
                Arguments.of("monitor/operlog/list", "/rows/0/operId"),
                Arguments.of("monitor/logininfor/list", "/rows/0/infoId"));
    }

    @BeforeEach void setup() throws Exception {
        SysUser user = SystemResponseContractTest.sample(SysUser.class);
        user.setDept(SystemResponseContractTest.sample(SysDept.class));
        user.setRoles(List.of(SystemResponseContractTest.sample(SysRole.class)));
        user.setPassword("fixture-password-never-return");
        user.setSalt("fixture-salt-never-return");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(user, Set.of()), "unused"));
        var session = new SystemSessionController(stub(TokenService.class), stub(SystemPermissionService.class), stub(ISysMenuService.class), stub(SysLoginService.class));
        ReflectionTestUtils.setField(session, "passwords", stub(PasswordPolicy.class));
        mvc = MockMvcBuilders.standaloneSetup(wired(SysUserController.class),
                wired(SysRoleController.class),
                wired(SysDeptController.class),
                wired(SysMenuController.class),
                wired(SysPostController.class),
                wired(SysDictDataController.class),
                wired(SysDictTypeController.class),
                wired(SysConfigController.class),
                wired(SysNoticeController.class),
                wired(SysAutoCodeRuleController.class),
                wired(SysAutoCodePartController.class),
                wired(SysMessageController.class),
                wired(SysOperlogController.class),
                wired(SysLogininforController.class),
                new SystemFileController(stub(SysAttachmentServiceImpl.class)),
                new SystemInboxController(stub(SysMessageServiceImpl.class), stub(MessageEventHub.class)),
                new SysProfileController(stub(SysProfileService.class), stub(SysAttachmentServiceImpl.class)),
                session).build();
    }

    @AfterEach void clearContext() {
        PageHelper.clearPage();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0}") @MethodSource("routes")
    void everyReviewedGetUsesAnExplicitContractWithoutAdvice(String path, String idPath) throws Exception {
        var response = mvc.perform(get("/api/" + path)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        JsonNode value = json.readTree(response.getContentAsString());
        JsonNode id = value.at(idPath);
        boolean nativeEntity = path.startsWith("system/config/") || path.startsWith("system/notice/");
        if (nativeEntity) assertTrue(id.isIntegralNumber(), path + " " + idPath);
        else assertTrue(id.isTextual(), path + " " + idPath);
        assertEquals(nativeEntity ? "42" : "9007199254740993", id.asText());
        SystemResponseContractTest.rejectInternalFields(value);
        assertFalse(response.getContentAsString().contains("fixture-password-never-return"));
        assertFalse(response.getContentAsString().contains("fixture-salt-never-return"));
        if (value.has("rows")) {
            assertTrue(value.get("total").isIntegralNumber());
            assertEquals(1, value.get("total").asInt());
        }
    }

    @Test void uploadReturnsOnlyTheExplicitFileWhitelist() throws Exception {
        var response = mvc.perform(multipart("/api/platform/files").file(new MockMultipartFile("file", "fixture.png", "image/png", new byte[]{1})).param("resource", "avatar")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        JsonNode value = json.readTree(response.getContentAsString());
        assertTrue(value.get("attachmentId").isTextual());
        assertTrue(value.get("ownerId").isTextual());
        assertTrue(value.get("fileSize").isNumber());
        SystemResponseContractTest.rejectInternalFields(value);
    }

    @Test void cacheSizeIsANumericCountWithoutAnyBusinessProjection() throws Exception {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.info()).thenReturn(new Properties());
        when(connection.info("commandstats")).thenReturn(new Properties());
        when(connection.dbSize()).thenReturn(12L);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> template = mock(RedisTemplate.class);
        when(template.execute(any(RedisCallback.class))).thenAnswer(call -> {
            RedisCallback<?> callback = call.getArgument(0);
            return callback.doInRedis(connection);
        });
        CacheController controller = new CacheController();
        ReflectionTestUtils.setField(controller, "redisTemplate", template);
        var response = MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/monitor/cache")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        JsonNode size = json.readTree(response.getContentAsString()).at("/data/dbSize");
        assertTrue(size.isIntegralNumber());
        assertEquals(12, size.asInt());
    }

    private <T> T wired(Class<T> type) throws Exception {
        T controller = type.getConstructor().newInstance();
        for (Field field : type.getDeclaredFields()) if (field.isAnnotationPresent(Autowired.class)) ReflectionTestUtils.setField(controller, field.getName(), stub(field.getType()));
        return controller;
    }

    private <T> T stub(Class<T> type) {
        return mock(type, invocation -> {
            String method = invocation.getMethod().getName();
            Class<?> returned = invocation.getMethod().getReturnType();
            if (method.equals("selectDictDataByType")) return List.of(SystemResponseContractTest.sample(SysDictData.class));
            if (method.equals("profile")) return new SysProfileService.Profile(SystemResponseContractTest.sample(SysUser.class), "role-fixture", "post-fixture");
            if (method.equals("selectPostListByUserId") || method.equals("selectDeptListByRoleId") || method.equals("selectMenuListByRoleId")) return List.of(9007199254740993L);
            if (method.equals("buildDeptTreeSelect") || method.equals("buildMenuTreeSelect")) return List.of(SystemResponseContractTest.sample(TreeSelect.class));
            Class<?> row = null;
            if (type == ISysUserService.class) row = SysUser.class;
            if (type == ISysRoleService.class) row = SysRole.class;
            if (type == ISysDeptService.class) row = SysDept.class;
            if (type == ISysMenuService.class) row = SysMenu.class;
            if (type == ISysPostService.class) row = SysPost.class;
            if (type == ISysDictDataService.class) row = SysDictData.class;
            if (type == ISysDictTypeService.class) row = SysDictType.class;
            if (type == ISysConfigService.class) row = SysConfig.class;
            if (type == ISysNoticeService.class) row = SysNotice.class;
            if (type == IAutoCodeRuleService.class) row = SysAutoCodeRule.class;
            if (type == IAutoCodePartService.class) row = SysAutoCodePart.class;
            if (type == ISysMessageService.class) row = SysMessage.class;
            if (type == ISysOperLogService.class) row = SysOperLog.class;
            if (type == ISysLogininforService.class) row = SysLogininfor.class;
            if (type == SysMessageServiceImpl.class) row = SysMessage.class;
            if (type == SysAttachmentServiceImpl.class) row = SysAttachment.class;
            if (row != null) {
                Object source = SystemResponseContractTest.sample(row);
                if (source instanceof SysUser user) {
                    user.setPassword("fixture-password-never-return");
                    user.setSalt("fixture-salt-never-return");
                    user.setDept(SystemResponseContractTest.sample(SysDept.class));
                    user.setRoles(List.of(SystemResponseContractTest.sample(SysRole.class)));
                }
                if (returned == List.class) return new ArrayList<>(List.of(source));
                if (returned == row) return source;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
