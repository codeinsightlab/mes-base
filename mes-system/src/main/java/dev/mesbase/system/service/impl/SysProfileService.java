package dev.mesbase.system.service.impl;
import dev.mesbase.common.core.domain.entity.SysUser;import dev.mesbase.common.exception.ServiceException;import dev.mesbase.common.utils.SecurityUtils;import dev.mesbase.system.service.ISysUserService;import dev.mesbase.common.utils.bean.BeanValidators;
@org.springframework.stereotype.Service @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysProfileService {
 private final ISysUserService users;private final jakarta.validation.Validator validator;private final dev.mesbase.common.utils.PasswordPolicy passwords;
 public SysProfileService(ISysUserService users,jakarta.validation.Validator validator,dev.mesbase.common.utils.PasswordPolicy passwords){this.users=users;this.validator=validator;this.passwords=passwords;}
 public record Profile(SysUser user,String roleGroup,String postGroup){}
 public Profile profile(){var user=users.selectUserById(SecurityUtils.getUserId());return new Profile(user,users.selectUserRoleGroup(user.getUserName()),users.selectUserPostGroup(user.getUserName()));}
 public int update(String nick,String phone,String email,String sex){var current=users.selectUserById(SecurityUtils.getUserId());var user=new SysUser(current.getUserId());user.setUserName(current.getUserName());user.setNickName(nick);user.setPhonenumber(phone);user.setEmail(email);user.setSex(sex);user.setUpdateBy(current.getUserName());BeanValidators.validateWithException(validator,user);if(phone!=null&&!phone.isBlank()&&"1".equals(users.checkPhoneUnique(user)))throw new ServiceException("手机号码已存在");if(email!=null&&!email.isBlank()&&"1".equals(users.checkEmailUnique(user)))throw new ServiceException("邮箱已存在");return users.updateUserProfile(user);}
 public int password(String oldPassword,String newPassword){var current=users.selectUserById(SecurityUtils.getUserId());passwords.validate(newPassword);if(!SecurityUtils.matchesPassword(oldPassword,current.getPassword()))throw new ServiceException("旧密码错误");if(SecurityUtils.matchesPassword(newPassword,current.getPassword()))throw new ServiceException("新密码不能与旧密码相同");return users.resetUserPwd(current.getUserName(),passwords.encode(newPassword));}
 public boolean avatar(String fileUrl){return users.updateUserAvatar(SecurityUtils.getUsername(),fileUrl,null);}
}
