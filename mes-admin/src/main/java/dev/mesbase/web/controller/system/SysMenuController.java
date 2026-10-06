package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.dto.MenuForm;
import dev.mesbase.system.service.ISysMenuService;
import dev.mesbase.web.view.menu.SysMenuAssembler;
import dev.mesbase.web.view.tree.TreeAssembler;
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
 * 菜单信息
 * 
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/menu")
public class SysMenuController extends BaseController
{
    @Autowired
    private ISysMenuService menuService;

    /**
     * 获取菜单列表
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:menu:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public AjaxResult list(SysMenu menu)
    {
        List<SysMenu> menus = menuService.selectMenuList(menu, getUserId());
        return AjaxResult.success(SysMenuAssembler.toResults(menus));
    }

    /**
     * 根据菜单编号获取详细信息
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:menu:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{menuId}")
    public AjaxResult getInfo(@PathVariable Long menuId)
    {
        return AjaxResult.success(SysMenuAssembler.toResult(menuService.selectMenuById(menuId)));
    }

    /**
     * 获取菜单下拉树列表
     */
    @GetMapping("/treeselect")
    @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult treeselect(SysMenu menu)
    {
        List<SysMenu> menus = menuService.selectMenuList(menu, getUserId());
        return AjaxResult.success(TreeAssembler.toResults(menuService.buildMenuTreeSelect(menus)));
    }

    /**
     * 加载对应角色菜单列表树
     */

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:role:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/roleMenuTreeselect/{roleId}")
    public AjaxResult roleMenuTreeselect(@PathVariable("roleId") Long roleId)
    {
        List<SysMenu> menus = menuService.selectMenuList(getUserId());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", menuService.selectMenuListByRoleId(roleId).stream().map(String::valueOf).toList());
        ajax.put("menus", TreeAssembler.toResults(menuService.buildMenuTreeSelect(menus)));
        return ajax;
    }

    /**
     * 新增菜单
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:menu:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody MenuForm menuForm)
    {
        SysMenu menu=menuForm.toEntity();
        menu.setMenuId(null);
        if (UserConstants.NOT_UNIQUE.equals(menuService.checkMenuNameUnique(menu)))
        {
            return AjaxResult.error("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        else if (UserConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath()))
        {
            return AjaxResult.error("新增菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        menu.setCreateBy(getUsername());
        return toAjax(menuService.insertMenu(menu));
    }

    /**
     * 修改菜单
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:menu:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody MenuForm menuForm)
    {
        SysMenu menu=menuForm.toEntity();
        if(menu.getMenuId()==null)throw new IllegalArgumentException("Menu identifier required");
        if (UserConstants.NOT_UNIQUE.equals(menuService.checkMenuNameUnique(menu)))
        {
            return AjaxResult.error("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        else if (UserConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath()))
        {
            return AjaxResult.error("修改菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        else if (menu.getMenuId().equals(menu.getParentId()))
        {
            return AjaxResult.error("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        }
        menu.setUpdateBy(getUsername());
        return toAjax(menuService.updateMenu(menu));
    }

    /**
     * 删除菜单
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:menu:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{menuId}")
    public AjaxResult remove(@PathVariable("menuId") Long menuId)
    {
        if (menuService.hasChildByMenuId(menuId))
        {
            return AjaxResult.error("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId))
        {
            return AjaxResult.error("菜单已分配,不允许删除");
        }
        return toAjax(menuService.deleteMenuById(menuId));
    }
}
