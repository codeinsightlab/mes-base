package dev.mesbase.web.controller.monitor;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysLogininfor;
import dev.mesbase.system.service.ISysLogininforService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.log.SysLogininforAssembler;
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
 * 系统访问记录
 * 
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/monitor/logininfor")
public class SysLogininforController extends BaseController
{
    @Autowired
    private ISysLogininforService logininforService;

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:logininfor:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysLogininfor logininfor)
    {
        startPage();
        List<SysLogininfor> list = logininforService.selectLogininforList(logininfor);
        return PageResultMapper.map(list, SysLogininforAssembler::toResult);
    }

    @Log(title = "登录日志", businessType = BusinessType.EXPORT)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:logininfor:export",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysLogininfor logininfor)
    {
        List<SysLogininfor> list = logininforService.selectLogininforList(logininfor);
        ExcelUtil<SysLogininfor> util = new ExcelUtil<SysLogininfor>(SysLogininfor.class);
        util.exportExcel(response, list, "登录日志");
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:logininfor:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public AjaxResult remove(@PathVariable Long[] infoIds)
    {
        return toAjax(logininforService.deleteLogininforByIds(infoIds));
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:logininfor:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "登录日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        logininforService.cleanLogininfor();
        return AjaxResult.success();
    }
}
