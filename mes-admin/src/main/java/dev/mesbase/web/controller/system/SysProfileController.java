package dev.mesbase.web.controller.system;
import dev.mesbase.common.core.controller.BaseController;import dev.mesbase.common.core.domain.AjaxResult;import dev.mesbase.common.annotation.Log;import dev.mesbase.common.enums.BusinessType;import dev.mesbase.platform.web.EndpointAccess;import dev.mesbase.system.service.impl.SysProfileService;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
/** SOURCE_PRODUCT_PORT: same profile workflow, server-owned identity and private avatar. */
@RestController @RequestMapping("/api/system/user/profile") @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysProfileController extends BaseController {
 private final SysProfileService profiles;private final dev.mesbase.system.service.impl.SysAttachmentServiceImpl files;
 public SysProfileController(SysProfileService profiles,dev.mesbase.system.service.impl.SysAttachmentServiceImpl files){this.profiles=profiles;this.files=files;}
 @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"userId","deptId","userName","userType","status","delFlag","loginIp","loginDate","avatar","dept","roles","roleIds","postIds","roleId","createTime","updateTime","createBy","updateBy","remark"})
 public record Form(String nickName,String phonenumber,String email,String sex){}
 public record Password(String oldPassword,String newPassword){}
 @GetMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult profile(){var value=profiles.profile();var a=AjaxResult.success(value.user());a.put("roleGroup",value.roleGroup());a.put("postGroup",value.postGroup());return a;}
 @PutMapping @Log(title="个人信息",businessType=BusinessType.UPDATE) @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult updateProfile(@RequestBody Form user){return toAjax(profiles.update(user.nickName(),user.phonenumber(),user.email(),user.sex()));}
 @PutMapping("/updatePwd") @Log(title="个人信息",businessType=BusinessType.UPDATE) @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult updatePwd(@RequestBody Password form){return toAjax(profiles.password(form.oldPassword(),form.newPassword()));}
 @PostMapping("/avatar") @Log(title="用户头像",businessType=BusinessType.UPDATE) @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file)throws java.io.IOException{var row=files.upload("avatar",file.getOriginalFilename(),file.getContentType(),file.getInputStream(),dev.mesbase.platform.execution.FactoryContext.current().orElse(null));profiles.avatar(row.getFileUrl());var a=AjaxResult.success();a.put("imgUrl",row.getFileUrl());return a;}
}
