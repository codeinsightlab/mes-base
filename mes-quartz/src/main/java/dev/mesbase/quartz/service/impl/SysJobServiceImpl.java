package dev.mesbase.quartz.service.impl;

import dev.mesbase.common.constant.ScheduleConstants;
import dev.mesbase.common.exception.job.TaskException;
import dev.mesbase.quartz.domain.SysJob;
import dev.mesbase.quartz.mapper.SysJobMapper;
import dev.mesbase.quartz.service.ISysJobService;
import dev.mesbase.quartz.util.CronUtils;
import dev.mesbase.quartz.util.ScheduleUtils;

import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import jakarta.annotation.PostConstruct;

/**
 * 定时任务调度信息 服务层
 * 
 * @author ruoyi
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysJobServiceImpl implements ISysJobService
{
    @Autowired
    private Scheduler scheduler;

    @Autowired
    private SysJobMapper jobMapper;
    @Autowired private dev.mesbase.framework.persistence.SystemUnit unit;
    @Autowired private dev.mesbase.quartz.util.ControlledJobRunner runner;
    private void after(dev.mesbase.quartz.util.SchedulerEffect effect){unit.afterCommit(()->{try{effect.run();}catch(Exception failure){throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.EXECUTION_UNKNOWN,failure);}});}
    private SysJob required(Long id){var j=jobMapper.selectJobById(id);if(j==null)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.NOT_FOUND);return j;}

    /**
     * 项目启动时，初始化定时器 主要是防止手动修改数据库导致未同步到定时任务处理（注：不能手动修改数据库ID和任务组名，否则会导致脏数据）
     */
    
    public void init() throws SchedulerException, TaskException
    {
        scheduler.clear();
        List<SysJob> jobList = jobMapper.selectJobAll();
        for (SysJob job : jobList)
        {
            after(()->ScheduleUtils.createScheduleJob(scheduler, job));
        }
    }

    /**
     * 获取quartz调度器的计划任务列表
     * 
     * @param job 调度信息
     * @return
     */
    @Override
    public List<SysJob> selectJobList(SysJob job)
    {
        return jobMapper.selectJobList(job);
    }

    /**
     * 通过调度任务ID查询调度信息
     * 
     * @param jobId 调度任务ID
     * @return 调度任务对象信息
     */
    @Override
    public SysJob selectJobById(Long jobId)
    {
        return jobMapper.selectJobById(jobId);
    }

    /**
     * 暂停任务
     * 
     * @param job 调度信息
     */
    @Override
    public int pauseJob(SysJob job) throws SchedulerException
    {
        Long jobId = job.getJobId();
        String jobGroup = job.getJobGroup();
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        int rows = jobMapper.updateJob(job);
        if (rows > 0)
        {
            after(()->scheduler.pauseJob(ScheduleUtils.getJobKey(jobId, jobGroup)));
        }
        return rows;
    }

    /**
     * 恢复任务
     * 
     * @param job 调度信息
     */
    @Override
    public int resumeJob(SysJob job) throws SchedulerException
    {
        Long jobId = job.getJobId();
        String jobGroup = job.getJobGroup();
        job.setStatus(ScheduleConstants.Status.NORMAL.getValue());
        int rows = jobMapper.updateJob(job);
        if (rows > 0)
        {
            after(()->scheduler.resumeJob(ScheduleUtils.getJobKey(jobId, jobGroup)));
        }
        return rows;
    }

    /**
     * 删除任务后，所对应的trigger也将被删除
     * 
     * @param job 调度信息
     */
    @Override
    public int deleteJob(SysJob job) throws SchedulerException
    {
        Long jobId = job.getJobId();
        String jobGroup = job.getJobGroup();
        int rows = jobMapper.deleteJobById(jobId);
        if(rows!=1)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.RESOURCE_CONFLICT);
        if (rows > 0)
        {
            after(()->scheduler.deleteJob(ScheduleUtils.getJobKey(jobId, jobGroup)));
        }
        return rows;
    }

    /**
     * 批量删除调度信息
     * 
     * @param jobIds 需要删除的任务ID
     * @return 结果
     */
    @Override
    public void deleteJobByIds(Long[] jobIds) throws SchedulerException
    {
        for (Long jobId : jobIds)
        {
            SysJob job = required(jobId);
            deleteJob(job);
        }
    }

    /**
     * 任务调度状态修改
     * 
     * @param job 调度信息
     */
    @Override
    public int changeStatus(SysJob job) throws SchedulerException
    {
        int rows = 0;
        String status = job.getStatus();
        if (ScheduleConstants.Status.NORMAL.getValue().equals(status))
        {
            rows = resumeJob(job);
        }
        else if (ScheduleConstants.Status.PAUSE.getValue().equals(status))
        {
            rows = pauseJob(job);
        }
        if(rows==0)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.BAD_REQUEST);return rows;
    }

    /**
     * 立即运行任务
     * 
     * @param job 调度信息
     */
    @Override
    public void run(SysJob job) throws SchedulerException
    {
        String requestId=org.slf4j.MDC.get("requestId");
        runner.run(job.getJobId(),String.valueOf(dev.mesbase.common.utils.SecurityUtils.getUserId()),requestId==null?java.util.UUID.randomUUID().toString():requestId);
    }

    /**
     * 新增任务
     * 
     * @param job 调度信息 调度信息
     */
    @Override
    public int insertJob(SysJob job) throws SchedulerException, TaskException
    {
        runner.validate(job);
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        int rows = jobMapper.insertJob(job);
        if (rows > 0)
        {
            after(()->ScheduleUtils.createScheduleJob(scheduler, job));
        }
        return rows;
    }

    /**
     * 更新任务的时间表达式
     * 
     * @param job 调度信息
     */
    @Override
    public int updateJob(SysJob job) throws SchedulerException, TaskException
    {
        runner.validate(job);
        SysJob properties = required(job.getJobId());
        int rows = jobMapper.updateJob(job);
        if (rows > 0)
        {
            after(()->updateSchedulerJob(job, properties.getJobGroup()));
        }
        return rows;
    }

    /**
     * 更新任务
     * 
     * @param job 任务对象
     * @param jobGroup 任务组名
     */
    public void updateSchedulerJob(SysJob job, String jobGroup) throws SchedulerException, TaskException
    {
        Long jobId = job.getJobId();
        // 判断是否存在
        JobKey jobKey = ScheduleUtils.getJobKey(jobId, jobGroup);
        if (scheduler.checkExists(jobKey))
        {
            // 防止创建时存在数据问题 先移除，然后在执行创建操作
            scheduler.deleteJob(jobKey);
        }
        ScheduleUtils.createScheduleJob(scheduler, job);
    }

    /**
     * 校验cron表达式是否有效
     * 
     * @param cronExpression 表达式
     * @return 结果
     */
    @Override
    public boolean checkCronExpressionIsValid(String cronExpression)
    {
        return CronUtils.isValid(cronExpression);
    }
}
