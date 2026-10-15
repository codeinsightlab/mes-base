package dev.mesbase.infrastructure.cache;

import dev.mesbase.system.domain.SysUserCacheResource;

import dev.mesbase.common.cache.DetailEntityCache;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

class UserReferenceCacheInvalidationTest {
    @Test
    void managedNameWriteInvalidatesOnlyAfterCommitAndRollbackDoesNotEvict() {
        var cache = mock(DetailEntityCache.class);
        var mapper = mock(SysUserMapper.class);
        when(mapper.updateUser(any())).thenReturn(1);
        var proxy = new AspectJProxyFactory(mapper);
        proxy.setInterfaces(SysUserMapper.class);
        proxy.addAspect(new UserReferenceCacheInvalidation(cache, new SpringCommitActions()));
        SysUserMapper users = proxy.getProxy();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            users.updateUser(new SysUser());
            verifyNoInteractions(cache);
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
            verify(cache).invalidate(SysUserCacheResource.PREFIX);
        } finally {
            TransactionSynchronizationManager.clear();
        }
        var other = mock(DetailEntityCache.class);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new UserReferenceCacheInvalidation(other, new SpringCommitActions()).changed(1);
            for (var callback : TransactionSynchronizationManager.getSynchronizations())
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(other);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
