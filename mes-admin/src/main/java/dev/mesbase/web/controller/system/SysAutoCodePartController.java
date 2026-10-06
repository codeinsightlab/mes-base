package dev.mesbase.web.controller.system;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;


import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.dto.AutoCodePartForm;
import dev.mesbase.system.service.IAutoCodePartService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.autocode.SysAutoCodePartAssembler;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;

@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/autocode/part")
public class SysAutoCodePartController extends BaseController {

    @Autowired
    private IAutoCodePartService iAutoCodePartService;

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysAutoCodePart sysAutoCodePart){
        startPage();
        List<SysAutoCodePart> parts = iAutoCodePartService.listPart(sysAutoCodePart);
        return PageResultMapper.map(parts, SysAutoCodePartAssembler::toResult);
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/{partId}")
    public AjaxResult getInfo(@PathVariable Long partId){
        return AjaxResult.success(SysAutoCodePartAssembler.toResult(iAutoCodePartService.findById(partId)));
    }


    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:insert",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "新增编码生产规则组成部分",businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AutoCodePartForm input){
        SysAutoCodePart part=input.toEntity();
        part.setCreateBy(getUsername());
        return toAjax(iAutoCodePartService.insertPart(part));
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:update",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "更新编码规则组成",businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@Validated @RequestBody AutoCodePartForm input){
        SysAutoCodePart sysAutoCodePart=input.toEntity();
        sysAutoCodePart.setUpdateBy(getUsername());
        return toAjax(iAutoCodePartService.updatePart(sysAutoCodePart));
    }


    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:part:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "删除编码规则组成",businessType = BusinessType.DELETE)
    @DeleteMapping("/{partIds}")
    public AjaxResult delete(@PathVariable Long[] partIds){
        return toAjax(iAutoCodePartService.deleteByIds(partIds));
    }

}
