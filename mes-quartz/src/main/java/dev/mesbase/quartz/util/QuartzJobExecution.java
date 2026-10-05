package dev.mesbase.quartz.util;

import dev.mesbase.quartz.domain.SysJob;

import org.quartz.JobExecutionContext;

/**
 * 定时任务处理（允许并发执行）
 * 
 * @author ruoyi
 *
 */
public class QuartzJobExecution extends AbstractQuartzJob
{
    @Override
    protected void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception
    {
        dev.mesbase.common.utils.spring.SpringUtils.getBean(ControlledJobRunner.class).run(sysJob.getJobId());
    }
}
