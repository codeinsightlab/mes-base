package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysNotice;
import dev.mesbase.system.dto.NoticeForm;
import dev.mesbase.system.service.ISysNoticeService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告 信息操作处理
 *
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
@RequestMapping("/api/system/notice")
public class SysNoticeController extends BaseController {
    @Autowired
    private ISysNoticeService noticeService;

    /**
     * 获取通知公告列表
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:notice:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysNotice notice) {
        startPage();
        List<SysNotice> list = noticeService.selectNoticeList(notice);
        return getDataTable(list);
    }

    /**
     * 根据通知公告编号获取详细信息
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:notice:query", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{noticeId}")
    public AjaxResult getInfo(@PathVariable Long noticeId) {
        return AjaxResult.success(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 新增通知公告
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:notice:add", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody NoticeForm noticeForm) {
        SysNotice notice = noticeForm.toEntity();
        notice.setNoticeId(null);
        notice.setCreateBy(getUsername());
        return toAjax(noticeService.insertNotice(notice));
    }

    /**
     * 修改通知公告
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:notice:edit", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody NoticeForm noticeForm) {
        SysNotice notice = noticeForm.toEntity();
        notice.setUpdateBy(getUsername());
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 删除通知公告
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:notice:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@PathVariable Long[] noticeIds) {
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }
}
