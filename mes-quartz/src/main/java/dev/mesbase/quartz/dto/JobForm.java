package dev.mesbase.quartz.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"nextValidTime","createBy","createTime","updateBy","updateTime","params","runningToken","startedAt"})
public record JobForm(Long jobId,String jobName,String jobGroup,String invokeTarget,String cronExpression,String misfirePolicy,String concurrent,String status,String executionScope,String factoryId,String remark){
 public dev.mesbase.quartz.domain.SysJob toEntity(){var j=new dev.mesbase.quartz.domain.SysJob();j.setJobId(jobId);j.setJobName(jobName);j.setJobGroup(jobGroup);j.setInvokeTarget(invokeTarget);j.setCronExpression(cronExpression);j.setMisfirePolicy(misfirePolicy);j.setConcurrent(concurrent);j.setStatus(status);j.setExecutionScope(executionScope);j.setFactoryId(factoryId);j.setRemark(remark);return j;}
}
