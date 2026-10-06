package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.dto.UserForm;
import dev.mesbase.system.service.ISysPostService;
import dev.mesbase.system.service.ISysRoleService;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.post.SysPostAssembler;
import dev.mesbase.web.view.role.SysRoleAssembler;
import dev.mesbase.web.view.user.SysUserAssembler;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息
 * 
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/user")
public class SysUserController extends BaseController
{
    @Autowired
    private ISysUserService userService;
    @Autowired private PasswordPolicy passwords;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysPostService postService;

    /**
     * 获取用户列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectUserList(user);
        return PageResultMapper.map(list, SysUserAssembler::toResult);
    }

    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:export",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysUser user)
    {
        List<SysUser> list = userService.selectUserList(user);
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.exportExcel(response, list, "用户数据");
    }

    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:import",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport,@RequestParam String initialPassword) throws Exception
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        List<SysUser> userList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = userService.importUser(userList, updateSupport, operName,initialPassword);
        return AjaxResult.success(message);
    }

    @PostMapping("/importTemplate")
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:import",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.importTemplateExcel(response, "用户数据");
    }

    /**
     * 根据用户编号获取详细信息
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = { "/", "/{userId}" })
    public AjaxResult getInfo(@PathVariable(value = "userId", required = false) Long userId)
    {
        if(userId!=null) userService.checkUserDataScope(userId);
        AjaxResult ajax = AjaxResult.success();
        List<SysRole> roles = roleService.selectRoleAll();
        ajax.put("roles", SysRoleAssembler.toResults(roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList())));
        ajax.put("posts", SysPostAssembler.toResults(postService.selectPostAll()));
        if (StringUtils.isNotNull(userId))
        {
            SysUser sysUser = userService.selectUserById(userId);
            ajax.put(AjaxResult.DATA_TAG, SysUserAssembler.toResult(sysUser));
            ajax.put("postIds", postService.selectPostListByUserId(userId).stream().map(String::valueOf).toList());
            ajax.put("roleIds", sysUser.getRoles().stream().map(role -> String.valueOf(role.getRoleId())).collect(Collectors.toList()));
        }
        return ajax;
    }

    /**
     * 新增用户
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody UserForm userForm)
    {
        SysUser user=userForm.toEntity();
        user.setUserId(null);
        if (UserConstants.NOT_UNIQUE.equals(userService.checkUserNameUnique(user.getUserName())))
        {
            return AjaxResult.error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber())
                && UserConstants.NOT_UNIQUE.equals(userService.checkPhoneUnique(user)))
        {
            return AjaxResult.error("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail())
                && UserConstants.NOT_UNIQUE.equals(userService.checkEmailUnique(user)))
        {
            return AjaxResult.error("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setCreateBy(getUsername());
        user.setPassword(passwords.encode(user.getPassword()));
        return toAjax(userService.insertUser(user));
    }

    /**
     * 修改用户
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody UserForm userForm)
    {
        SysUser user=userForm.toEntity();
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        if (StringUtils.isNotEmpty(user.getPhonenumber())
                && UserConstants.NOT_UNIQUE.equals(userService.checkPhoneUnique(user)))
        {
            return AjaxResult.error("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail())
                && UserConstants.NOT_UNIQUE.equals(userService.checkEmailUnique(user)))
        {
            return AjaxResult.error("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setUpdateBy(getUsername());
        return toAjax(userService.updateUser(user));
    }

    /**
     * 删除用户
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable Long[] userIds)
    {
        if (ArrayUtils.contains(userIds, getUserId()))
        {
            return error("当前用户不能删除");
        }
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 重置密码
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:resetPwd",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody UserForm userForm)
    {
        SysUser user=userForm.toEntity();
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(passwords.encode(user.getPassword()));
        user.setUpdateBy(getUsername());
        return toAjax(userService.resetPwd(user));
    }

    /**
     * 状态修改
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody UserForm userForm)
    {
        SysUser user=userForm.toEntity();
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setUpdateBy(getUsername());
        return toAjax(userService.updateUserStatus(user));
    }

    /**
     * 根据用户编号获取授权角色
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/authRole/{userId}")
    public AjaxResult authRole(@PathVariable("userId") Long userId)
    {
        userService.checkUserDataScope(userId);
        AjaxResult ajax = AjaxResult.success();
        SysUser user = userService.selectUserById(userId);
        List<SysRole> roles = roleService.selectRolesByUserId(userId);
        ajax.put("user", SysUserAssembler.toResult(user));
        ajax.put("roles", SysRoleAssembler.toResults(roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList())));
        return ajax;
    }

    /**
     * 用户授权角色
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:user:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/authRole")
    public AjaxResult insertAuthRole(Long userId, Long[] roleIds)
    {
        if(userId!=null) userService.checkUserDataScope(userId);
        userService.insertUserAuth(userId, roleIds);
        return success();
    }
}
