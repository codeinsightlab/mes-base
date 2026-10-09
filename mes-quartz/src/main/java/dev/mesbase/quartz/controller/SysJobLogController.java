package dev.mesbase.quartz.controller;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.quartz.domain.SysJobLog;
import dev.mesbase.quartz.dto.JobViews;
import dev.mesbase.quartz.service.ISysJobLogService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调度日志操作处理
 *
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
@RequestMapping("/api/monitor/jobLog")
public class SysJobLogController extends BaseController {
    @Autowired
    private ISysJobLogService jobLogService;

    /**
     * 查询定时任务调度日志列表
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:job:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysJobLog sysJobLog) {
        startPage();
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        var result = getDataTable(list);
        result.setRows(list.stream().map(JobViews::log).toList());
        return result;
    }

    /**
     * 导出定时任务调度日志列表
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:job:export", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "任务调度日志", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysJobLog sysJobLog) {
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        ExcelUtil<SysJobLog> util = new ExcelUtil<SysJobLog>(SysJobLog.class);
        util.exportExcel(response, list, "调度日志");
    }

    /**
     * 根据调度编号获取详细信息
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:job:query", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{jobLogId}")
    public AjaxResult getInfo(@PathVariable Long jobLogId) {
        return AjaxResult.success(JobViews.log(jobLogService.selectJobLogById(jobLogId)));
    }


    /**
     * 删除定时任务调度日志
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:job:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "定时任务调度日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{jobLogIds}")
    public AjaxResult remove(@PathVariable Long[] jobLogIds) {
        return toAjax(jobLogService.deleteJobLogByIds(jobLogIds));
    }

    /**
     * 清空定时任务调度日志
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:job:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "调度日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public AjaxResult clean() {
        jobLogService.cleanJobLog();
        return AjaxResult.success();
    }
}
