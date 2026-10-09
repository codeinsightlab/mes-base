package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.dto.ConfigForm;
import dev.mesbase.system.service.ISysConfigService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 参数配置 信息操作处理
 *
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
@RequestMapping("/api/system/config")
public class SysConfigController extends BaseController {
    @Autowired
    private ISysConfigService configService;

    /**
     * 获取参数配置列表
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysConfig config) {
        startPage();
        List<SysConfig> list = configService.selectConfigList(config);
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:export", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysConfig config) {
        List<SysConfig> list = configService.selectConfigList(config);
        ExcelUtil<SysConfig> util = new ExcelUtil<SysConfig>(SysConfig.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:query", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId) {
        return AjaxResult.success(configService.selectConfigById(configId));
    }

    /**
     * 根据参数键名查询参数值
     */
    @GetMapping(value = "/configKey/{configKey}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult getConfigKey(@PathVariable String configKey) {
        return AjaxResult.success((Object) configService.selectClientConfigByKey(configKey));
    }

    /**
     * 新增参数配置
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:add", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ConfigForm configForm) {
        SysConfig config = configForm.toEntity();
        config.setConfigId(null);
        if (UserConstants.NOT_UNIQUE.equals(configService.checkConfigKeyUnique(config))) {
            return AjaxResult.error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setCreateBy(getUsername());
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改参数配置
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:edit", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ConfigForm configForm) {
        SysConfig config = configForm.toEntity();
        if (UserConstants.NOT_UNIQUE.equals(configService.checkConfigKeyUnique(config))) {
            return AjaxResult.error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setUpdateBy(getUsername());
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 删除参数配置
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds) {
        configService.deleteConfigByIds(configIds);
        return success();
    }

    /**
     * 刷新参数缓存
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:config:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache() {
        configService.resetConfigCache();
        return AjaxResult.success();
    }
}
