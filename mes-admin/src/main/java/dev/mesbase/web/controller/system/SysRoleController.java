package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.framework.web.service.SysPermissionService;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysUserRole;
import dev.mesbase.system.dto.RoleForm;
import dev.mesbase.system.service.ISysRoleService;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.role.SysRoleAssembler;
import dev.mesbase.web.view.user.SysUserAssembler;
import jakarta.servlet.http.HttpServletResponse;
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

/**
 * 角色信息
 * 
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/role")
public class SysRoleController extends BaseController
{
    public record RoleStatus(Long roleId,String status){}
    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private SysPermissionService permissionService;
    
    @Autowired
    private ISysUserService userService;

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysRole role)
    {
        startPage();
        List<SysRole> list = roleService.selectRoleList(role);
        return PageResultMapper.map(list, SysRoleAssembler::toResult);
    }

    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:export",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysRole role)
    {
        List<SysRole> list = roleService.selectRoleList(role);
        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
        util.exportExcel(response, list, "角色数据");
    }

    /**
     * 根据角色编号获取详细信息
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{roleId}")
    public AjaxResult getInfo(@PathVariable Long roleId)
    {
        roleService.checkRoleDataScope(roleId);
        return AjaxResult.success(SysRoleAssembler.toResult(roleService.selectRoleById(roleId)));
    }

    /**
     * 新增角色
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody RoleForm roleForm)
    {
        SysRole role=roleForm.toEntity();
        role.setRoleId(null);
        if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleNameUnique(role)))
        {
            return AjaxResult.error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleKeyUnique(role)))
        {
            return AjaxResult.error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setCreateBy(getUsername());
        return toAjax(roleService.insertRole(role));

    }

    /**
     * 修改保存角色
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody RoleForm roleForm)
    {
        SysRole role=roleForm.toEntity();
        // 校验角色是否允许操作
        roleService.checkRoleAllowed(role);
        // 校验角色是否有数据权限
        roleService.checkRoleDataScope(role.getRoleId());
        if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleNameUnique(role)))
        {
            return AjaxResult.error("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleKeyUnique(role)))
        {
            return AjaxResult.error("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setUpdateBy(getUsername());
        
        if (roleService.updateRole(role) > 0)
        {
            // 更新缓存用户权限
            LoginUser loginUser = getLoginUser();
            if (StringUtils.isNotNull(loginUser.getUser()) && !loginUser.getUser().isAdmin())
            {
                loginUser.setPermissions(permissionService.getMenuPermission(loginUser.getUser()));
                loginUser.setUser(userService.selectUserByUserName(loginUser.getUser().getUserName()));
                tokenService.setLoginUser(loginUser);
            }
            return AjaxResult.success();
        }
        return AjaxResult.error("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改保存数据权限
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/dataScope")
    public AjaxResult dataScope(@RequestBody RoleForm roleForm)
    {
        SysRole role=roleForm.toEntity();
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return toAjax(roleService.authDataScope(role));
    }

    /**
     * 状态修改
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody RoleStatus roleForm)
    {
        SysRole role=new SysRole();role.setRoleId(roleForm.roleId());role.setStatus(roleForm.status());
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        role.setUpdateBy(getUsername());
        return toAjax(roleService.updateRoleStatus(role));
    }

    /**
     * 删除角色
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public AjaxResult remove(@PathVariable Long[] roleIds)
    {
        return toAjax(roleService.deleteRoleByIds(roleIds));
    }

    /**
     * 获取角色选择框列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        return AjaxResult.success(SysRoleAssembler.toResults(roleService.selectRoleAll()));
    }

    /**
     * 查询已分配用户角色列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/authUser/allocatedList")
    public TableDataInfo allocatedList(SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectAllocatedList(user);
        return PageResultMapper.map(list, SysUserAssembler::toResult);
    }

    /**
     * 查询未分配用户角色列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/authUser/unallocatedList")
    public TableDataInfo unallocatedList(SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectUnallocatedList(user);
        return PageResultMapper.map(list, SysUserAssembler::toResult);
    }

    /**
     * 取消授权用户
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancel")
    public AjaxResult cancelAuthUser(@RequestBody SysUserRole userRole)
    {
        return toAjax(roleService.deleteAuthUser(userRole));
    }

    /**
     * 批量取消授权用户
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancelAll")
    public AjaxResult cancelAuthUserAll(Long roleId, Long[] userIds)
    {
        return toAjax(roleService.deleteAuthUsers(roleId, userIds));
    }

    /**
     * 批量选择用户授权
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/selectAll")
    public AjaxResult selectAuthUserAll(Long roleId, Long[] userIds)
    {
        roleService.checkRoleDataScope(roleId);
        return toAjax(roleService.insertAuthUsers(roleId, userIds));
    }
}
