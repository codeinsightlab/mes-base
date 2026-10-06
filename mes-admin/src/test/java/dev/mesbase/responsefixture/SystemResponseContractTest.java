package dev.mesbase.responsefixture;

import dev.mesbase.common.core.domain.TreeSelect;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.system.domain.SysAttachment;
import dev.mesbase.system.domain.SysLogininfor;
import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.domain.SysOperLog;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.web.view.autocode.SysAutoCodePartAssembler;
import dev.mesbase.web.view.autocode.SysAutoCodeRuleAssembler;
import dev.mesbase.web.view.dept.SysDeptAssembler;
import dev.mesbase.web.view.dict.SysDictDataAssembler;
import dev.mesbase.web.view.dict.SysDictTypeAssembler;
import dev.mesbase.web.view.file.SysAttachmentAssembler;
import dev.mesbase.web.view.log.SysLogininforAssembler;
import dev.mesbase.web.view.log.SysOperLogAssembler;
import dev.mesbase.web.view.menu.SysMenuAssembler;
import dev.mesbase.web.view.message.SysMessageAssembler;
import dev.mesbase.web.view.post.SysPostAssembler;
import dev.mesbase.web.view.role.SysRoleAssembler;
import dev.mesbase.web.view.tree.TreeAssembler;
import dev.mesbase.web.view.user.SysUserAssembler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import dev.mesbase.web.view.PageResultMapper;
import java.math.BigDecimal;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.domain.SysNotice;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixture compatibility against the frozen old projection, including internal-field rejection. */
class SystemResponseContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final long ID = 9007199254740993L;

    static Stream<Arguments> contracts() {
        return Stream.of(Arguments.of(TreeSelect.class, (Function<TreeSelect, ?>) TreeAssembler::toResult),
                Arguments.of(SysRole.class, (Function<SysRole, ?>) SysRoleAssembler::toResult),
                Arguments.of(SysPost.class, (Function<SysPost, ?>) SysPostAssembler::toResult),
                Arguments.of(SysAutoCodePart.class, (Function<SysAutoCodePart, ?>) SysAutoCodePartAssembler::toResult),
                Arguments.of(SysAutoCodeRule.class, (Function<SysAutoCodeRule, ?>) SysAutoCodeRuleAssembler::toResult),
                Arguments.of(SysAttachment.class, (Function<SysAttachment, ?>) SysAttachmentAssembler::toResult),
                Arguments.of(SysMessage.class, (Function<SysMessage, ?>) SysMessageAssembler::toResult),
                Arguments.of(SysUser.class, (Function<SysUser, ?>) SysUserAssembler::toResult),
                Arguments.of(SysMenu.class, (Function<SysMenu, ?>) SysMenuAssembler::toResult),
                Arguments.of(SysDept.class, (Function<SysDept, ?>) SysDeptAssembler::toResult),
                Arguments.of(SysLogininfor.class, (Function<SysLogininfor, ?>) SysLogininforAssembler::toResult),
                Arguments.of(SysOperLog.class, (Function<SysOperLog, ?>) SysOperLogAssembler::toResult),
                Arguments.of(SysDictType.class, (Function<SysDictType, ?>) SysDictTypeAssembler::toResult),
                Arguments.of(SysDictData.class, (Function<SysDictData, ?>) SysDictDataAssembler::toResult));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contracts")
    <T> void explicitResultMatchesLegacyFieldNamesTypesDatesAndNulls(Class<T> type, Function<T, ?> convert) throws Exception {
        T empty = type.getConstructor().newInstance();
        assertEquals(json.valueToTree(LegacySystemViews.project(empty)), json.valueToTree(convert.apply(empty)));
        assertEquals(null, convert.apply(null));
        T source = sample(type);
        if (source instanceof SysUser user) {
            user.setDept(sample(SysDept.class));
            user.setRoles(List.of(sample(SysRole.class)));
            user.setPassword("fixture-password-never-return");
            user.setSalt("fixture-salt-never-return");
        }
        if (source instanceof SysDept dept) dept.setChildren(List.of(sample(SysDept.class)));
        if (source instanceof SysMenu menu) menu.setChildren(List.of(sample(SysMenu.class)));
        if (source instanceof TreeSelect tree) tree.setChildren(List.of(sample(TreeSelect.class)));
        JsonNode actual = json.valueToTree(convert.apply(source));
        assertEquals(json.valueToTree(LegacySystemViews.project(source)), actual);
        rejectInternalFields(actual);
    }

    static <T> T sample(Class<T> type) throws Exception {
        T value = type.getConstructor().newInstance();
        for (Method method : type.getMethods()) {
            if (!method.getName().startsWith("set") || method.getParameterCount() != 1) continue;
            Class<?> field = method.getParameterTypes()[0];
            Object argument;
            if (field == String.class) argument = method.getName().endsWith("Sort") || method.getName().equals("setOrderNum") ? "7" : "fixture";
            else if (field == Long.class || field == long.class) argument = type == SysConfig.class || type == SysNotice.class ? 42L : ID;
            else if (field == Integer.class || field == int.class) argument = 7;
            else if (field == Boolean.class || field == boolean.class) argument = true;
            else if (field == Date.class) argument = new Date(1700000000000L);
            else if (field == BigDecimal.class) argument = new BigDecimal("1234.50");
            else if (field == Long[].class) argument = new Long[]{ID, 2L};
            else if (field == Integer[].class) argument = new Integer[]{1, 2};
            else if (Map.class.isAssignableFrom(field)) argument = Map.of("internal-fixture-key", "internal-fixture-value");
            else continue;
            method.invoke(value, argument);
        }
        return value;
    }

    static void rejectInternalFields(JsonNode node) {
        for (String field : List.of("password", "salt", "secret", "params", "searchValue")) assertFalse(node.has(field), field);
        for (JsonNode child : node) if (child.isContainerNode()) rejectInternalFields(child);
    }

    static Stream<Class<?>> safeCrudEntities() {
        return Stream.of(SysConfig.class, SysNotice.class);
    }

    @ParameterizedTest @MethodSource("safeCrudEntities")
    void safeCrudUsesNativeIntSchemaIdsWithoutFrameworkMetadata(Class<?> type) throws Exception {
        Object source = sample(type);
        JsonNode actual = json.valueToTree(source);
        JsonNode legacy = json.valueToTree(LegacySystemViews.project(source));
        rejectInternalFields(actual);
        assertFalse(actual.toString().contains("internal-fixture-value"));
        var fields = legacy.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            assertTrue(actual.has(field.getKey()));
            if (List.of("configId", "noticeId").contains(field.getKey())) {
                assertTrue(actual.get(field.getKey()).isIntegralNumber());
                assertEquals("42", actual.get(field.getKey()).asText());
            } else assertEquals(field.getValue(), actual.get(field.getKey()));
        }
    }

    @Test void pageTotalIsCapturedBeforeRowsAreConvertedAndNeverStringified() throws Exception {
        Page<SysUser> source = new Page<>(2, 1);
        source.setTotal(42);
        source.add(sample(SysUser.class));
        var result = PageResultMapper.map(source, SysUserAssembler::toResult);
        JsonNode response = json.valueToTree(result);
        assertEquals(42, result.getTotal());
        assertTrue(response.get("total").isIntegralNumber());
        assertEquals("9007199254740993", response.at("/rows/0/userId").asText());
        assertTrue(response.at("/rows/0/userId").isTextual());
        assertEquals(0, PageResultMapper.map(new ArrayList<SysUser>(), SysUserAssembler::toResult).getTotal());
    }

    @Test void businessNumbersRetainTheirNumericTypes() throws Exception {
        JsonNode dict = json.valueToTree(SysDictDataAssembler.toResult(sample(SysDictData.class)));
        assertTrue(dict.get("dictSort").isIntegralNumber());
        assertTrue(dict.get("dictCode").isTextual());
        JsonNode log = json.valueToTree(SysOperLogAssembler.toResult(sample(SysOperLog.class)));
        assertTrue(log.get("costTime").isIntegralNumber());
        JsonNode file = json.valueToTree(SysAttachmentAssembler.toResult(sample(SysAttachment.class)));
        assertTrue(file.get("fileSize").isNumber());
    }
}
