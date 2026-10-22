package dev.mesbase.quartz.dto;
import dev.mesbase.quartz.domain.*;import java.util.Date;
public final class JobViews {
 public record JobView(String jobId,String jobName,String jobGroup,String invokeTarget,String cronExpression,String misfirePolicy,String concurrent,String status,String executionScope,String factoryId,String remark,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")Date nextValidTime,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")Date createTime){}
 public static JobView job(SysJob j){if (j == null) return null;
        return new JobView(String.valueOf(j.getJobId()),j.getJobName(),j.getJobGroup(),j.getInvokeTarget(),j.getCronExpression(),j.getMisfirePolicy(),j.getConcurrent(),j.getStatus(),j.getExecutionScope(),j.getFactoryId(),j.getRemark(),j.getNextValidTime(),j.getCreateTime());}
 public record LogView(String requestId,String actorId,String factoryId,String executionScope,String runToken,Long costTime,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")Date startTime,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")Date stopTime,String jobLogId,String jobName,String jobGroup,String invokeTarget,String jobMessage,String status,String exceptionInfo,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")Date createTime){}
 public static LogView log(SysJobLog j){if (j == null) return null;
        return new LogView(j.getRequestId(),j.getActorId(),j.getFactoryId(),j.getExecutionScope(),j.getRunToken(),j.getCostTime(),j.getStartTime(),j.getStopTime(),String.valueOf(j.getJobLogId()),j.getJobName(),j.getJobGroup(),j.getInvokeTarget(),j.getJobMessage(),j.getStatus(),j.getExceptionInfo(),j.getCreateTime());}
}
