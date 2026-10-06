package dev.mesbase.web.controller.monitor;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysOperLog;
import dev.mesbase.system.service.ISysOperLogService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.log.SysOperLogAssembler;
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
 * 操作日志记录
 * 
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/monitor/operlog")
public class SysOperlogController extends BaseController
{
    @Autowired
    private ISysOperLogService operLogService;

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:operlog:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysOperLog operLog)
    {
        startPage();
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        return PageResultMapper.map(list, SysOperLogAssembler::toResult);
    }

    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:operlog:export",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysOperLog operLog)
    {
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        ExcelUtil<SysOperLog> util = new ExcelUtil<SysOperLog>(SysOperLog.class);
        util.exportExcel(response, list, "操作日志");
    }

    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:operlog:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @DeleteMapping("/{operIds}")
    public AjaxResult remove(@PathVariable Long[] operIds)
    {
        return toAjax(operLogService.deleteOperLogByIds(operIds));
    }

    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:operlog:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        operLogService.cleanOperLog();
        return AjaxResult.success();
    }
}
