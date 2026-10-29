package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.dto.AutoCodeRuleDocument;
import dev.mesbase.system.dto.AutoCodeRuleDocumentForm;
import dev.mesbase.web.view.autocode.SysAutoCodePartAssembler;
import dev.mesbase.web.view.autocode.SysAutoCodePartResult;
import dev.mesbase.web.view.autocode.SysAutoCodeRuleResult;
import dev.mesbase.system.service.IAutoCodeRuleService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.autocode.SysAutoCodeRuleAssembler;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/autocode/rule")
public class SysAutoCodeRuleController extends BaseController {

    @Autowired
    private IAutoCodeRuleService iAutoCodeRuleService;

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysAutoCodeRule sysAutoCodeRule){
        startPage();
        List<SysAutoCodeRule> rules = iAutoCodeRuleService.selectAutoCodeList(sysAutoCodeRule);
        return PageResultMapper.map(rules, SysAutoCodeRuleAssembler::toResult);
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/{ruleId}")
    public AjaxResult getInfo(@PathVariable Long ruleId){
        return AjaxResult.success(SysAutoCodeRuleAssembler.toResult(iAutoCodeRuleService.findById(ruleId)));
    }

    public record RuleDocumentResult(SysAutoCodeRuleResult rule, List<SysAutoCodePartResult> parts,
                                     String version, boolean prefixEditable) { }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/{ruleId}/document")
    public AjaxResult document(@PathVariable Long ruleId) {
        return result(iAutoCodeRuleService.document(ruleId));
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "新增自动编码规则及组成",businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AutoCodeRuleDocumentForm input) {
        if (input.rule() == null) throw new ServiceException("请提交规则及前缀配置");
        var rule = input.rule().toEntity();
        return result(iAutoCodeRuleService.createDocument(rule, input.prefix(), getUsername()));
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "更新自动编码规则及组成",businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult updateInfo(@Validated @RequestBody AutoCodeRuleDocumentForm input) {
        if (input.rule() == null) throw new ServiceException("请提交规则及前缀配置");
        var rule = input.rule().toEntity();
        return result(iAutoCodeRuleService.updateDocument(rule, input.prefix(), input.version(), getUsername()));
    }

    private static AjaxResult result(AutoCodeRuleDocument document) {
        return AjaxResult.success(new RuleDocumentResult(SysAutoCodeRuleAssembler.toResult(document.rule()),
                SysAutoCodePartAssembler.toResults(document.parts()), document.version(), document.prefixEditable()));
    }

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "删除自动编码规则",businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds){

        return toAjax(iAutoCodeRuleService.deleteByIds(ruleIds));
    }

}
