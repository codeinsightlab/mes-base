package dev.mesbase.platform.management;
import jakarta.servlet.http.*;import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import dev.mesbase.platform.execution.FactoryContext;
public final class PlatformAuditInterceptor implements HandlerInterceptor {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PlatformAuditInterceptor.class);
 private final PlatformService service;public PlatformAuditInterceptor(PlatformService service){this.service=service;}
 public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception){
  if(!request.getRequestURI().startsWith("/api/")||request.getRequestURI().startsWith("/api/platform/audit"))return;
  var auth=SecurityContextHolder.getContext().getAuthentication();String actor=auth==null||auth instanceof AnonymousAuthenticationToken?"ANONYMOUS":auth.getName();
  String scope=FactoryContext.current().map(s->s.factoryId().value()).orElse(null);String id=(String)request.getAttribute("mes.requestId");
  // Log only route shape, not path values/query/body. RequestMapping pattern is safe catalog metadata.
  Object pattern=request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
  dev.mesbase.infrastructure.platform.PlatformRows.JobScope executionScope=scope==null?dev.mesbase.infrastructure.platform.PlatformRows.JobScope.PLATFORM:dev.mesbase.infrastructure.platform.PlatformRows.JobScope.FACTORY;
  if(handler instanceof org.springframework.web.method.HandlerMethod method){var access=method.getMethodAnnotation(dev.mesbase.platform.web.EndpointAccess.class);if(access!=null&&access.permissionScope()==dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)executionScope=dev.mesbase.infrastructure.platform.PlatformRows.JobScope.PLATFORM;}
  try{if(!actor.equals("ANONYMOUS"))actor=service.actorId(actor);service.audit(id,actor,scope,executionScope,request.getMethod()+" "+pattern,response.getStatus()<400?"SUCCEEDED":"REJECTED");}
  catch(RuntimeException failed){LOG.error("Audit persistence failed requestId={}",id,failed);}
 }
}
