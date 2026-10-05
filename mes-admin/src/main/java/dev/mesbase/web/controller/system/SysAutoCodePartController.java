package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.system.service.IAutoCodePartService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/autocode/part")
public class SysAutoCodePartController extends BaseController {

    @Autowired
    private IAutoCodePartService iAutoCodePartService;

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:list",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysAutoCodePart sysAutoCodePart){
        startPage();
        List<SysAutoCodePart> parts = iAutoCodePartService.listPart(sysAutoCodePart);
        return getDataTable(parts);
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:query",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/{partId}")
    public AjaxResult getInfo(@PathVariable Long partId){
        return AjaxResult.success(iAutoCodePartService.findById(partId));
    }


    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:insert",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "新增编码生产规则组成部分",businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody dev.mesbase.system.dto.AutoCodePartForm input){
        SysAutoCodePart part=input.toEntity();
        part.setCreateBy(getUsername());
        return toAjax(iAutoCodePartService.insertPart(part));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:update",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "更新编码规则组成",businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@Validated @RequestBody dev.mesbase.system.dto.AutoCodePartForm input){
        SysAutoCodePart sysAutoCodePart=input.toEntity();
        sysAutoCodePart.setUpdateBy(getUsername());
        return toAjax(iAutoCodePartService.updatePart(sysAutoCodePart));
    }


    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:remove",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "删除编码规则组成",businessType = BusinessType.DELETE)
    @DeleteMapping("/{partIds}")
    public AjaxResult delete(@PathVariable Long[] partIds){
        return toAjax(iAutoCodePartService.deleteByIds(partIds));
    }

}
