package dev.mesbase.web.controller.system;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.system.service.impl.SysAutoCodeGenerationServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** Source SysAutoCodeController PORT: generation is POST, explicitly authorized and audited. */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/autocode")
public class SysAutoCodeController {
 public record Input(String ruleCode,String inputCharacter){}
 public record GeneratedCode(String code){}
 private final SysAutoCodeGenerationServiceImpl service;
 public SysAutoCodeController(SysAutoCodeGenerationServiceImpl service){this.service=service;}
 @PostMapping("/preview") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult preview(@RequestBody Input input){return AjaxResult.success(new GeneratedCode(service.preview(input.ruleCode(), input.inputCharacter())));}
 @PostMapping("/generate") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:autocode:rule:generate",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @Log(title="生成平台编码",businessType=BusinessType.INSERT)
 public AjaxResult generate(@RequestBody Input input){return AjaxResult.success(new GeneratedCode(service.generate(input.ruleCode(), input.inputCharacter())));}
}
