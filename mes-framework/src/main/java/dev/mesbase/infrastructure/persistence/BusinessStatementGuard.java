package dev.mesbase.infrastructure.persistence;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.*;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
@Intercepts({
    @Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),
    @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),
    @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class,CacheKey.class,BoundSql.class}),
    @Signature(type=Executor.class,method="queryCursor",args={MappedStatement.class,Object.class,RowBounds.class})
})
final class BusinessStatementGuard implements Interceptor {
    private final Runnable verify;
    BusinessStatementGuard(Runnable verify) { this.verify=verify; }
    public Object intercept(Invocation invocation) throws Throwable { verify.run(); return invocation.proceed(); }
}
