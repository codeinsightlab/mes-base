package dev.mesbase.quartz.controller;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.Constants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.exception.job.TaskException;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.quartz.domain.SysJob;
import dev.mesbase.quartz.service.ISysJobService;
import dev.mesbase.quartz.util.CronUtils;
import dev.mesbase.quartz.util.ScheduleUtils;

import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 调度任务信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/monitor/job")
public class SysJobController extends BaseController
{
    @Autowired
    private ISysJobService jobService;

    /**
     * 查询定时任务列表
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:list",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysJob sysJob)
    {
        startPage();
        List<SysJob> list = jobService.selectJobList(sysJob);
        var result=getDataTable(list);result.setRows(list.stream().map(dev.mesbase.quartz.dto.JobViews::job).toList());return result;
    }

    /**
     * 导出定时任务列表
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:export",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysJob sysJob)
    {
        List<SysJob> list = jobService.selectJobList(sysJob);
        ExcelUtil<SysJob> util = new ExcelUtil<SysJob>(SysJob.class);
        util.exportExcel(response, list, "定时任务");
    }

    /**
     * 获取定时任务详细信息
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:query",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{jobId}")
    public AjaxResult getInfo(@PathVariable("jobId") Long jobId)
    {
        return AjaxResult.success(dev.mesbase.quartz.dto.JobViews.job(jobService.selectJobById(jobId)));
    }

    /**
     * 新增定时任务
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:add",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody dev.mesbase.quartz.dto.JobForm form) throws SchedulerException, TaskException
    {
        var job=form.toEntity();
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return error("新增任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不在白名单内");
        }
        job.setCreateBy(getUsername());
        return toAjax(jobService.insertJob(job));
    }

    /**
     * 修改定时任务
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:edit",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody dev.mesbase.quartz.dto.JobForm form) throws SchedulerException, TaskException
    {
        var job=form.toEntity();
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return error("修改任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不在白名单内");
        }
        job.setUpdateBy(getUsername());
        return toAjax(jobService.updateJob(job));
    }

    /**
     * 定时任务状态修改
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:changeStatus",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody dev.mesbase.quartz.dto.JobForm form) throws SchedulerException
    {
        var job=form.toEntity();SysJob newJob = jobService.selectJobById(job.getJobId());
        if(newJob==null)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.NOT_FOUND);
        newJob.setStatus(job.getStatus());
        return toAjax(jobService.changeStatus(newJob));
    }

    /**
     * 定时任务立即执行一次
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:changeStatus",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/run")
    public AjaxResult run(@RequestBody dev.mesbase.quartz.dto.JobForm form) throws SchedulerException
    {
        jobService.run(form.toEntity());
        return AjaxResult.success();
    }

    /**
     * 删除定时任务
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:remove",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{jobIds}")
    public AjaxResult remove(@PathVariable Long[] jobIds) throws SchedulerException, TaskException
    {
        jobService.deleteJobByIds(jobIds);
        return AjaxResult.success();
    }

    public record Recovery(Long jobId,String expectedRunToken,boolean confirmedProcessDead){}
    @PostMapping("/recover")
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:changeStatus",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title="任务恢复",businessType=BusinessType.UPDATE)
    public AjaxResult recover(@RequestBody Recovery form){dev.mesbase.common.utils.spring.SpringUtils.getBean(dev.mesbase.quartz.util.ControlledJobRunner.class).recover(form.jobId(),form.expectedRunToken(),form.confirmedProcessDead(),String.valueOf(getUserId()),org.slf4j.MDC.get("requestId"));return AjaxResult.success();}
 @GetMapping("/{id}/run-state")
 @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="monitor:job:query",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult runState(@PathVariable Long id){return AjaxResult.success(dev.mesbase.common.utils.spring.SpringUtils.getBean(dev.mesbase.quartz.util.ControlledJobRunner.class).state(id));}
}
