package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.lang.reflect.Proxy;
import java.util.Set;
import org.apache.ibatis.session.SqlSession;

/** No raw connection, SQL string or SqlSession escapes to services. */
public final class OwnedSqlSession {
    private final SqlSession delegate; private final Set<Class<?>> mappers; private final Runnable verify;
    private final Thread ownerThread=Thread.currentThread();
    private boolean closed;
    OwnedSqlSession(SqlSession session,Set<Class<?>> mappers,Runnable verify) { this.delegate=session; this.mappers=mappers; this.verify=verify; }
    public <T> T mapper(Class<T> type) {
        check(); if(!mappers.contains(type)) throw new FoundationException(ErrorCode.FORBIDDEN);
        T mapper=delegate.getMapper(type);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args) -> {
            check();
            return Proxy.getInvocationHandler(mapper).invoke(mapper,method,args);
        }));
    }
    private void check() { if(closed || Thread.currentThread()!=ownerThread) throw new FoundationException(ErrorCode.CONFLICT); verify.run(); }
    void expire() { closed=true; }
    void close() { expire(); delegate.close(); }
}
