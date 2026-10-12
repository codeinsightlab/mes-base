package dev.mesbase.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import java.lang.reflect.Method;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transaction advisor must wrap this advisor: lease admission precedes every cache hit.
 */
@Aspect
@Component
@Order(BusinessCacheAspect.ORDER)
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class BusinessCacheAspect {
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 10;
    public static final int TRANSACTION_ORDER = Ordered.LOWEST_PRECEDENCE - 20;
    private final BusinessCacheProcessor caches;
    private final OwnedTransactionManager transactions;

    public BusinessCacheAspect(BusinessCacheProcessor caches,
                               @Qualifier("businessTransactionManager") OwnedTransactionManager transactions) {
        this.caches = caches;
        this.transactions = transactions;
    }

    @Around("@annotation(intent)")
    public Object read(ProceedingJoinPoint call, BusinessCache intent) throws Throwable {
        String prefix = caches.prefix(intent.business());
        Object[] args = call.getArgs();
        if (intent.argument() < 0 || intent.argument() >= args.length)
            throw new IllegalArgumentException("Business cache argument index invalid");
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) call.getSignature()).getMethod(), call.getTarget()
                .getClass());
        JavaType type = caches.returnType(method.getGenericReturnType());
        if (type.isJavaLangObject() || type.hasRawClass(Void.TYPE))
            throw new IllegalArgumentException("Business cache requires a concrete return type");
        Object argument = args[intent.argument()];
        String key;
        if (intent.type() == BusinessCache.Type.LIST) {
            if (!(argument instanceof BusinessCacheQuery query))
                throw new IllegalArgumentException("LIST argument must implement BusinessCacheQuery");
            if (query.cacheSort() == null)
                throw new IllegalArgumentException("Business cache effective ordering required");
            if (query.cacheOffset() < 0 || query.cacheLimit() < 1 || query.cacheOffset() >= 100) return call.proceed();
            key = prefix + "list:" + caches.queryHash(args, query);
        } else {
            if (!(argument instanceof Long || argument instanceof Integer || argument instanceof String))
                throw new IllegalArgumentException("DETAIL argument must be a non-null String/Long/Integer ID");
            key = prefix + "detail:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(argument.toString().getBytes(StandardCharsets.UTF_8));
        }
        transactions.verifyAccess();
        // Never reuse/populate shared query data from a write transaction (including an outer write unit).
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            return call.proceed();
        Object hit = caches.read(key, type);
        if (hit != null) {
            transactions.verifyAccess();
            return hit;
        }
        Object result = call.proceed();
        transactions.verifyAccess();
        caches.write(key, result);
        return result;
    }

    @Around("@annotation(intent)")
    public Object evict(ProceedingJoinPoint call, BusinessCacheEvict intent) throws Throwable {
        String captured = caches.prefix(intent.business());
        Object result = call.proceed();
        caches.evictCaptured(captured, intent.scope());
        return result;
    }
}
