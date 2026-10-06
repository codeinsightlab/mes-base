package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

@Intercepts({
    @Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),
    @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),
    @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class,CacheKey.class,BoundSql.class}),
    @Signature(type=Executor.class,method="queryCursor",args={MappedStatement.class,Object.class,RowBounds.class})
})
final class PersistenceStatementGuard implements Interceptor {
    private final Runnable verify;
    PersistenceStatementGuard(Runnable verify) { this.verify=verify; }
    public Object intercept(Invocation invocation) throws Throwable { if (PersistenceReadContext.active() && ((MappedStatement) invocation.getArgs()[0]).getSqlCommandType() != SqlCommandType.SELECT)
            throw new FoundationException(ErrorCode.CONFLICT);
        verify.run(); return invocation.proceed(); }
}
