package dev.mesbase.infrastructure.persistence;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

/** Only connection selection; Spring's transaction interceptor handles the transaction. */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public final class ErpSourceAspect {
    @Around("@within(dev.mesbase.infrastructure.persistence.ErpSource) || @annotation(dev.mesbase.infrastructure.persistence.ErpSource)")
    public Object select(ProceedingJoinPoint invocation) throws Throwable {
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) invocation.getSignature()).getMethod(), invocation.getTarget().getClass());
        ErpSource source = AnnotatedElementUtils.findMergedAnnotation(method, ErpSource.class);
        if (source == null) source = AnnotatedElementUtils.findMergedAnnotation(invocation.getTarget().getClass(), ErpSource.class);
        String previous = ErpSourceContext.select(source.value());
        try { return invocation.proceed(); }
        finally { ErpSourceContext.restore(previous); }
    }
}
