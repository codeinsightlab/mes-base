package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.dto.MessageForm;
import dev.mesbase.system.service.ISysMessageService;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.system.service.impl.SysMessageServiceImpl;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.message.SysMessageAssembler;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息Controller
 *
 * @author yinjinlu
 * @date 2023-03-06
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/message")
public class SysMessageController extends BaseController
{
    @Autowired
    private ISysMessageService sysMessageService;
    @Autowired
    private ISysUserService sysUserService;
    @Autowired private SysMessageServiceImpl messages;

    /**
     * 查询消息列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:message:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysMessage sysMessage)
    {
        startPage();
        List<SysMessage> list = sysMessageService.selectSysMessageList(sysMessage);
        return PageResultMapper.map(list, SysMessageAssembler::toResult);
    }

    /**
     * 导出消息列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:message:export",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
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
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:message:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{messageId}")
    public AjaxResult getInfo(@PathVariable("messageId") Long messageId)
    {
        return AjaxResult.success(SysMessageAssembler.toResult(sysMessageService.selectSysMessageByMessageId(messageId)));
    }

    /**
     * 新增消息
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:message:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody MessageForm form)
    {
        messages.insertSysMessage(form.toEntity());
        return AjaxResult.success();
    }

    /**
     * 修改消息
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:message:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody MessageForm form)
    {
        return toAjax(messages.updateSysMessage(form.toEntity()));
    }

    /**
     * 删除消息
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:message:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "消息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{messageIds}")
    public AjaxResult remove(@PathVariable Long[] messageIds)
    {
        return toAjax(messages.archive(messageIds));
    }
}
