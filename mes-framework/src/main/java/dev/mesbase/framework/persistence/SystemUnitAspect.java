package dev.mesbase.framework.persistence;
import org.aspectj.lang.annotation.*;import org.aspectj.lang.ProceedingJoinPoint;
@Aspect @org.springframework.core.annotation.Order(0)
public final class SystemUnitAspect {
 private final SystemUnit unit; public SystemUnitAspect(SystemUnit unit){this.unit=unit;}
 @Around("(execution(public * dev.mesbase.system.service.impl.*.*(..)) || execution(public * dev.mesbase.quartz.service.impl.*.*(..)) || execution(public * dev.mesbase.system.service.SystemPermissionService.*(..))) && !execution(* dev.mesbase.quartz.service.impl.SysJobServiceImpl.run(..))") public Object transaction(ProceedingJoinPoint call){return unit.run(()->{try{return call.proceed();}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException(failure);}});}
}