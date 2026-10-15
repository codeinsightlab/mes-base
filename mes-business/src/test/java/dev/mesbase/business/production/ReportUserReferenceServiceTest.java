package dev.mesbase.business.production;

import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.OwnedSqlSession;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;
import java.util.function.Function;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportUserReferenceServiceTest {
    @Test
    void ordinaryHistoricalReferenceUsesInjectedMapperAndDoesNotRequireCurrentActiveAuthorization() {
        var users = mock(SysUserMapper.class);
        var center = mock(PlatformPersistence.class);
        var tx = mock(OwnedTransactionManager.class);
        when(center.transactionManager()).thenReturn(tx);
        var historical = new SysUser();
        historical.setUserId(7L);
        historical.setStatus("1");
        historical.setDelFlag("2");
        when(users.selectUserById(7L)).thenReturn(historical);
        new ReportUserReferenceService(users, center).requireExists(7L);
        verify(users).selectUserById(7L);
        verify(center, never()).readOnly(any());
        assertThrows(FoundationException.class, () -> new ReportUserReferenceService(users, center).requireExists(8L));
    }

    @Test
    void batchNamesDeduplicateIdsAndUseOneInjectedQueryWithoutSensitiveProfileLookup() {
        var mapper = mock(SysUserMapper.class);
        var center = mock(PlatformPersistence.class);
        when(center.transactionManager()).thenReturn(mock(OwnedTransactionManager.class));
        var user = new SysUser();
        user.setUserId(7L);
        user.setNickName("历史昵称");
        when(mapper.selectUserNamesByIds(List.of(7L, 8L))).thenReturn(List.of(user));
        var service = new ReportUserReferenceService(mapper, center);
        assertEquals(Map.of(7L, "历史昵称"), service.names(List.of(8L, 7L, 7L)));
        verify(mapper, times(1)).selectUserNamesByIds(List.of(7L, 8L));
        verify(mapper, never()).selectUserById(any());
        verify(center, never()).readOnly(any());
        assertEquals(Map.of(), service.names(List.of()));
    }

    @Test
    void actualForeignTransactionUsesTheExistingProtectedCenterReadWindow() {
        var injected = mock(SysUserMapper.class);
        var independent = mock(SysUserMapper.class);
        var center = mock(PlatformPersistence.class);
        var tx = mock(OwnedTransactionManager.class);
        var session = mock(OwnedSqlSession.class);
        when(center.transactionManager()).thenReturn(tx);
        when(tx.foreignTransactionActive()).thenReturn(true);
        when(session.mapper(SysUserMapper.class)).thenReturn(independent);
        when(independent.selectUserById(7L)).thenReturn(new SysUser());
        when(center.readOnly(any())).thenAnswer(call -> {
            Function<OwnedSqlSession, Object> read = call.getArgument(0);
            return read.apply(session);
        });
        new ReportUserReferenceService(injected, center).requireExists(7L);
        verify(center).readOnly(any());
        verifyNoInteractions(injected);
    }
}
