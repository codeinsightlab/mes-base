package dev.mesbase.server;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.mes.pro.personnelanalysis.mapper.ProReportingWorkPersonnelExtraMapper;
import dev.mesbase.mes.pro.productionmonitor.mapper.*;
import dev.mesbase.system.mapper.SysUserDisplayExtraMapper;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.ProceedingJoinPoint;
@Configuration @ConditionalOnProperty(name="mes.analytics.enabled",havingValue="true")
public class AnalyticsConfiguration {
 @Bean BusinessMapperRegistration personnelRegistration(){return new BusinessMapperRegistration(ProReportingWorkPersonnelExtraMapper.class);}
 @Bean BusinessMapperRegistration workordersRegistration(){return new BusinessMapperRegistration(ProWorkorderMonitorExtraMapper.class);}
 @Bean BusinessMapperRegistration reportingRegistration(){return new BusinessMapperRegistration(ProReportingWorkMonitorExtraMapper.class);}
 @Bean BusinessMapperRegistration defectiveRegistration(){return new BusinessMapperRegistration(QcDefectiveMonitorExtraMapper.class);}
 @Bean ProReportingWorkPersonnelExtraMapper personnelMapper(AnalyticsUnit unit){return unit.mapper(ProReportingWorkPersonnelExtraMapper.class);}
 @Bean ProWorkorderMonitorExtraMapper workordersMapper(AnalyticsUnit unit){return unit.mapper(ProWorkorderMonitorExtraMapper.class);}
 @Bean ProReportingWorkMonitorExtraMapper reportingMapper(AnalyticsUnit unit){return unit.mapper(ProReportingWorkMonitorExtraMapper.class);}
 @Bean QcDefectiveMonitorExtraMapper defectiveMapper(AnalyticsUnit unit){return unit.mapper(QcDefectiveMonitorExtraMapper.class);}
 @Bean SysUserDisplayExtraMapper userDisplayMapper(PlatformPersistence platform){
  return (SysUserDisplayExtraMapper)java.lang.reflect.Proxy.newProxyInstance(SysUserDisplayExtraMapper.class.getClassLoader(),new Class<?>[]{SysUserDisplayExtraMapper.class},(proxy,method,args)->{
   if(method.getDeclaringClass()==Object.class)return method.invoke(platform,args);
   return platform.readOnly(s->{try{return method.invoke(s.mapper(SysUserDisplayExtraMapper.class),args);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new IllegalStateException(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
  });
 }
 @Bean AnalyticsUnit analyticsUnit(BusinessPersistence persistence){return new AnalyticsUnit(persistence);}
 @Bean AnalyticsAspect analyticsAspect(AnalyticsUnit unit){return new AnalyticsAspect(unit);}
 public static final class AnalyticsUnit {
  private final BusinessPersistence persistence;private final ThreadLocal<OwnedSqlSession> current=new ThreadLocal<>();
  AnalyticsUnit(BusinessPersistence persistence){this.persistence=persistence;}
  Object run(java.util.function.Supplier<Object> action){if(current.get()!=null)return action.get();try{return persistence.transaction(dev.mesbase.platform.execution.FactoryContext.required(),s->{current.set(s);try{return action.get();}finally{current.remove();}});}finally{com.github.pagehelper.PageHelper.clearPage();}}
  <T>T mapper(Class<T> type){return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
   if(method.getDeclaringClass()==Object.class)return method.invoke(this,args);
   var session=current.get();if(session==null)throw new IllegalStateException("Analytics mapper requires business scope");
   try{return method.invoke(session.mapper(type),args);}catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
  }));}
 }
 @Aspect public static final class AnalyticsAspect {
  private final AnalyticsUnit unit;AnalyticsAspect(AnalyticsUnit unit){this.unit=unit;}
  @Around("execution(public * dev.mesbase.mes.pro.personnelanalysis.service.PersonnelAnalysisServiceImpl.*(..)) || execution(public * dev.mesbase.mes.pro.productionmonitor.service.impl.ProductionMonitorServiceImpl.*(..))")
  public Object read(ProceedingJoinPoint call){return unit.run(()->{try{return call.proceed();}catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}});}
 }
}
