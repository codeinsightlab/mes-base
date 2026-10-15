package dev.mesbase.infrastructure.cache;

import dev.mesbase.system.domain.SysUserCacheResource;

import dev.mesbase.common.cache.DetailEntityCache;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * All managed account name writes invalidate display references only after their transaction commits.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class UserReferenceCacheInvalidation {
    private final DetailEntityCache cache;
    private final SpringCommitActions commits;

    public UserReferenceCacheInvalidation(DetailEntityCache cache, SpringCommitActions commits) {
        this.cache = cache;
        this.commits = commits;
    }

    @AfterReturning(pointcut = "execution(* dev.mesbase.system.mapper.SysUserMapper.insertUser(..)) || execution(* dev.mesbase.system.mapper.SysUserMapper.updateUser(..)) || execution(* dev.mesbase.system.mapper.SysUserMapper.deleteUserById(..)) || execution(* dev.mesbase.system.mapper.SysUserMapper.deleteUserByIds(..))", returning = "changed")
    public void changed(int changed) {
        if (changed <= 0) return;
        Runnable action = () -> cache.invalidate(SysUserCacheResource.PREFIX);
        if (TransactionSynchronizationManager.isActualTransactionActive()) commits.afterCommit(action);
        else action.run();
    }
}
