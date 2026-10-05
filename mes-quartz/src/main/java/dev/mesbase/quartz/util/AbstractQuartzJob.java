package dev.mesbase.quartz.util;
import org.quartz.*;import dev.mesbase.quartz.domain.SysJob;import dev.mesbase.common.constant.ScheduleConstants;
/** PORT: source timing/dispatch, owned runner controls claim, scope, logs and cleanup. */
public abstract class AbstractQuartzJob implements Job {
 public void execute(JobExecutionContext context)throws JobExecutionException{try{doExecute(context,(SysJob)context.getMergedJobDataMap().get(ScheduleConstants.TASK_PROPERTIES));}catch(Exception failure){throw new JobExecutionException(failure);}}
 protected abstract void doExecute(JobExecutionContext context,SysJob job)throws Exception;
}