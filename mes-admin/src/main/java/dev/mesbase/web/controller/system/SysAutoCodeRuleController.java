package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.system.service.IAutoCodeRuleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/autocode/rule")
public class SysAutoCodeRuleController extends BaseController {

    @Autowired
    private IAutoCodeRuleService iAutoCodeRuleService;

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:list",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysAutoCodeRule sysAutoCodeRule){
        startPage();
        List<SysAutoCodeRule> rules = iAutoCodeRuleService.selectAutoCodeList(sysAutoCodeRule);
        return getDataTable(rules);
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:query",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/{ruleId}")
    public AjaxResult getInfo(@PathVariable Long ruleId){
        return AjaxResult.success(iAutoCodeRuleService.findById(ruleId));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:add",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "新增自动编码规则",businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody dev.mesbase.system.dto.AutoCodeRuleForm input){
        SysAutoCodeRule sysAutoCodeRule=input.toEntity();
        if("N".equals(sysAutoCodeRule.getIsPadded())){
            sysAutoCodeRule.setPaddedChar(null);
            sysAutoCodeRule.setPaddedMethod(null);
        }
        sysAutoCodeRule.setCreateBy(getUsername());
        return toAjax(iAutoCodeRuleService.insertInfo(sysAutoCodeRule));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:edit",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "更新自动编码规则",businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult updateInfo(@Validated @RequestBody dev.mesbase.system.dto.AutoCodeRuleForm input){
        SysAutoCodeRule sysAutoCodeRule=input.toEntity();
        sysAutoCodeRule.setUpdateBy(getUsername());
        return toAjax(iAutoCodeRuleService.updateInfo(sysAutoCodeRule));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:remove",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "删除自动编码规则",businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds){

        return toAjax(iAutoCodeRuleService.deleteByIds(ruleIds));
    }

}
