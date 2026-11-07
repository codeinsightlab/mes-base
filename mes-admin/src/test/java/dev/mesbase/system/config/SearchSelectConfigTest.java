package dev.mesbase.system.config;

import dev.mesbase.common.core.redis.CenterSettingsCache;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.mapper.SysConfigMapper;
import dev.mesbase.system.service.SystemPermissionService;
import dev.mesbase.system.service.impl.SysConfigServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchSelectConfigTest {
    private static final String KEY = "sys.ui.searchSelect.limit";

    private SysConfig config(String value) {
        var config = new SysConfig();
        config.setConfigName("Search option limit");
        config.setConfigKey(KEY);
        config.setConfigValue(value);
        config.setConfigType("Y");
        return config;
    }

    @Test
    void authenticatedClientReadDoesNotRequireConfigManagementPermission() {
        var mapper = mock(SysConfigMapper.class);
        var permissions = mock(SystemPermissionService.class);
        var service = new SysConfigServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "settingsCache", mock(CenterSettingsCache.class));
        ReflectionTestUtils.setField(service, "permissions", permissions);
        when(mapper.selectConfig(any())).thenReturn(config("20"));
        assertEquals("20", service.selectClientConfigByKey(KEY));
        verifyNoInteractions(permissions);
    }

    @Test
    void invalidCandidateLimitsNeverReachPersistenceOnCreateOrUpdate() {
        var mapper = mock(SysConfigMapper.class);
        var service = new SysConfigServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        for (String value : new String[]{"", "0", "-1", "101", "1.5", "abc", "999999999999"}) {
            assertThrows(FoundationException.class, () -> service.insertConfig(config(value)));
            assertThrows(FoundationException.class, () -> service.updateConfig(config(value)));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void validLimitsArePersistedAndInvalidateTheExistingSettingsCache() {
        var mapper = mock(SysConfigMapper.class);
        var cache = mock(CenterSettingsCache.class);
        var service = new SysConfigServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "settingsCache", cache);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ReflectionTestUtils.setField(service, "contractValidator", factory.getValidator());
            when(mapper.insertConfig(any())).thenReturn(1);
            assertEquals(1, service.insertConfig(config("20")));
            var updated = config("50");
            updated.setConfigId(1L);
            when(mapper.selectConfig(any())).thenReturn(config("20"));
            when(mapper.updateConfig(any())).thenReturn(1);
            assertEquals(1, service.updateConfig(updated));
            verify(mapper).updateConfig(updated);
            verify(cache, atLeastOnce()).invalidate("sys_config:" + KEY);
        }
    }
}
