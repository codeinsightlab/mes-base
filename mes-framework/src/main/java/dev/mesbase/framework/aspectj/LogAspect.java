package dev.mesbase.framework.aspectj;

import java.util.Optional;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.ServletUtils;
import dev.mesbase.system.domain.SysOperLog;
import dev.mesbase.system.service.ISysOperLogService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Around;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import dev.mesbase.platform.execution.FactoryContext;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * SOURCE_PRODUCT_PORT of RuoYi audit; metadata only, never bodies, credentials or query strings.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class LogAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAspect.class);
    private final ISysOperLogService service;

    public LogAspect(ISysOperLogService service) {
        this.service = service;
    }

    @Around("@annotation(action)")
    public Object audit(ProceedingJoinPoint call, Log action) throws Throwable {
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            return call.proceed();
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            try {
                var request = ServletUtils.getRequest();
                var row = new SysOperLog();
                row.setTitle(action.title());
                row.setBusinessType(action.businessType().ordinal());
                row.setOperatorType(action.operatorType().ordinal());
                row.setMethod(call.getSignature().getDeclaringTypeName() + "." + call.getSignature().getName());
                row.setRequestMethod(request.getMethod());
                row.setOperUrl(request.getRequestURI());
                row.setOperIp(request.getRemoteAddr());
                row.setRequestId(String.valueOf(request.getAttribute("mes.requestId")));
                row.setOperName(SecurityUtils.getUsername());
                row.setActorId(SecurityUtils.getUserId().toString());
                row.setFactoryId(FactoryContext.current().map(s -> s.factoryId().value()).orElse(null));
                row.setExecutionScope(row.getFactoryId() == null ? "PLATFORM" : "FACTORY");
                row.setStatus(failure == null ? 0 : 1);
                row.setErrorMsg(Optional.ofNullable(failure).map(nullableValue -> nullableValue.getClass().getSimpleName()).orElse(null));
                row.setCostTime((System.nanoTime() - started) / 1000000L);
                service.insertOperlog(row);
            } catch (Exception error) {
                LOGGER.error("Audit persistence failed", error);
            }
        }
    }
}
