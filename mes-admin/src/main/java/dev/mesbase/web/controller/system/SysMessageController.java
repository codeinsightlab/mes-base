package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;

import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.service.ISysMessageService;
import dev.mesbase.system.service.ISysUserService;

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
 * 消息Controller
 * 
 * @author yinjinlu
 * @date 2023-03-06
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/message")
public class SysMessageController extends BaseController
{
    @Autowired
    private ISysMessageService sysMessageService;
    @Autowired
    private ISysUserService sysUserService;
    @Autowired private dev.mesbase.system.service.impl.SysMessageServiceImpl messages;

    /**
     * 查询消息列表
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:message:list",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysMessage sysMessage)
    {
        startPage();
        List<SysMessage> list = sysMessageService.selectSysMessageList(sysMessage);
        return getDataTable(list);
    }

    /**
     * 导出消息列表
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:message:export",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysMessage sysMessage)
    {
        List<SysMessage> list = sysMessageService.selectSysMessageList(sysMessage);
        ExcelUtil<SysMessage> util = new ExcelUtil<SysMessage>(SysMessage.class);
        util.exportExcel(response, list, "消息数据");
    }

    /**
     * 获取消息详细信息
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:message:query",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{messageId}")
    public AjaxResult getInfo(@PathVariable("messageId") Long messageId)
    {
        return AjaxResult.success(sysMessageService.selectSysMessageByMessageId(messageId));
    }

    /**
     * 新增消息
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:message:add",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody dev.mesbase.system.dto.MessageForm form)
    {
        messages.insertSysMessage(form.toEntity());
        return AjaxResult.success();
    }

    /**
     * 修改消息
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:message:edit",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody dev.mesbase.system.dto.MessageForm form)
    {
        return toAjax(messages.updateSysMessage(form.toEntity()));
    }

    /**
     * 删除消息
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:message:remove",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{messageIds}")
    public AjaxResult remove(@PathVariable Long[] messageIds)
    {
        return toAjax(messages.archive(messageIds));
    }
}
