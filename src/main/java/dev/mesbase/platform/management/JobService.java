package dev.mesbase.platform.management;
import java.util.*;import java.time.*;import org.quartz.*;import org.quartz.impl.StdSchedulerFactory;
import dev.mesbase.core.factory.*;import dev.mesbase.core.error.*;import dev.mesbase.platform.execution.ExecutionScope;
import dev.mesbase.infrastructure.platform.PlatformRows.*;
import dev.mesbase.platform.management.PlatformContracts.*;
/** Quartz owns timing only; PLATFORM DB owns claims. No bean-name reflection or user-supplied class. */
public final class JobService implements AutoCloseable {
 public record RegisteredHandler(JobScope scope,Runnable action) {}
 private final PlatformStore store;private final FactoryCatalog factories;private final Map<String,RegisteredHandler> handlers;private final Scheduler scheduler;
 public JobService(PlatformStore store,FactoryCatalog factories,Map<String,RegisteredHandler> handlers)throws SchedulerException{
  this.store=store;this.factories=factories;this.handlers=Map.copyOf(handlers);
  Properties p=new Properties();p.setProperty("org.quartz.scheduler.instanceName","mes-base-"+UUID.randomUUID());p.setProperty("org.quartz.threadPool.threadCount","2");p.setProperty("org.quartz.threadPool.makeThreadsDaemons","true");p.setProperty("org.quartz.scheduler.makeSchedulerThreadDaemon","true");p.setProperty("org.quartz.jobStore.class","org.quartz.simpl.RAMJobStore");scheduler=new StdSchedulerFactory(p).getScheduler();scheduler.getContext().put("service",this);
 }
 static JobView view(dev.mesbase.infrastructure.platform.PlatformRows.Job j){return new JobView(j.id(),j.name(),j.handler(),j.executionScope(),j.factoryId(),j.cron(),j.status(),j.runningToken()!=null);}
 void validate(JobInput in){var h=handlers.get(in.handler());if(h==null||h.scope()!=in.executionScope()||!CronExpression.isValidExpression(in.cron()))throw new FoundationException(ErrorCode.BAD_REQUEST);if(in.executionScope()==JobScope.FACTORY){if(in.factoryId()==null)throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);var f=factories.find(new FactoryId(in.factoryId())).orElseThrow(()->new FoundationException(ErrorCode.FACTORY_NOT_FOUND));if(f.status()!=FactoryStatus.ENABLED)throw new FoundationException(ErrorCode.FACTORY_DISABLED);}else if(in.factoryId()!=null)throw new FoundationException(ErrorCode.BAD_REQUEST);}
 public List<JobView> list(int offset,int limit){PlatformService.page(offset,limit);return store.run(m->m.listJob(offset,limit).stream().map(JobService::view).toList());}
 public JobView save(String id,JobInput in){validate(in);var row=store.run(m->{String key=id==null?PlatformService.id():id;var j=new dev.mesbase.infrastructure.platform.PlatformRows.Job(key,in.name(),in.handler(),in.executionScope(),in.factoryId(),in.cron(),in.status(),null,null);if(id==null)PlatformService.changed(m.insertJob(j));else{PlatformService.required(m.findJob(id));PlatformService.changed(m.updateJob(j));}return j;});schedule(row);return view(row);}
 public void delete(String id){store.run(m->{PlatformService.required(m.findJob(id));PlatformService.changed(m.deleteJob(id));return null;});try{scheduler.deleteJob(JobKey.jobKey(id));}catch(SchedulerException e){throw new IllegalStateException("Quartz removal failed; reconcile required",e);}}
 public void start(){try{scheduler.start();}catch(SchedulerException e){throw new IllegalStateException(e);}reconcile();}
 public void reconcile(){int offset=0;while(true){final int page=offset;var rows=store.run(m->m.listJob(page,100));for(var row:rows)schedule(row);if(rows.size()<100)break;offset+=100;}}
 private void schedule(dev.mesbase.infrastructure.platform.PlatformRows.Job row){try{scheduler.deleteJob(JobKey.jobKey(row.id()));if(row.status()==Status.ENABLED){var job=JobBuilder.newJob(Dispatch.class).withIdentity(row.id()).usingJobData("id",row.id()).build();var cron=CronScheduleBuilder.cronSchedule(row.cron()).inTimeZone(TimeZone.getTimeZone("UTC")).withMisfireHandlingInstructionDoNothing();scheduler.scheduleJob(job,TriggerBuilder.newTrigger().withIdentity(row.id()).withSchedule(cron).build());}}catch(SchedulerException e){throw new IllegalStateException("Quartz scheduling failed; reconcile required",e);}}
 public List<String> handlerNames(){return handlers.keySet().stream().sorted().toList();}
 public void runForActor(String id,String username,String requestId){String actorId=store.run(m->PlatformService.required(m.userByUsername(username)).id());run(id,actorId,requestId);}
 public void run(String id,String actor,String requestId){
  var row=store.run(m->PlatformService.required(m.findJob(id)));validate(new JobInput(row.name(),row.handler(),row.executionScope(),row.factoryId(),row.cron(),row.status()));if(row.status()!=Status.ENABLED)throw new FoundationException(ErrorCode.UNPROCESSABLE);
  String token=PlatformService.id();int claimed=store.run(m->m.claimJob(id,token,PlatformService.now()));if(claimed!=1)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
  RuntimeException failure=null;String outcome="SUCCEEDED";
  try{var scope=row.executionScope()==JobScope.PLATFORM?ExecutionScope.platform():ExecutionScope.factory(new FactoryScope(new FactoryId(row.factoryId())));scope.run(handlers.get(row.handler()).action());}
  catch(RuntimeException e){failure=e;outcome="UNKNOWN";}
  final String result=outcome;
  try{store.run(m->{PlatformService.changed(m.releaseJob(id,token));m.insertJobLog(new JobLog(PlatformService.id(),id,requestId,actor,row.factoryId(),row.executionScope(),result,PlatformService.now()));return null;});}catch(RuntimeException logging){if(failure!=null)logging.addSuppressed(failure);throw logging;}
  if(failure!=null)throw failure;
 }
 @DisallowConcurrentExecution public static class Dispatch implements org.quartz.Job {
  public void execute(JobExecutionContext context)throws JobExecutionException{try{((JobService)context.getScheduler().getContext().get("service")).run(context.getMergedJobDataMap().getString("id"),"SCHEDULER",UUID.randomUUID().toString());}catch(Exception e){throw new JobExecutionException(e);}}
 }
 public void close()throws SchedulerException{scheduler.shutdown(true);}
 public PageView<JobView> listPage(int offset,int limit){var rows=list(offset,limit);return new PageView<>(rows,store.run(m->m.countJob()),offset,limit);}
}
