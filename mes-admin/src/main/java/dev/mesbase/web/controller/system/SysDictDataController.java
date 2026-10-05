package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.system.service.ISysDictDataService;
import dev.mesbase.system.service.ISysDictTypeService;

import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 数据字典信息
 * 
 * @author ruoyi
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/dict/data")
public class SysDictDataController extends BaseController
{
    @Autowired
    private ISysDictDataService dictDataService;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:dict:list",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysDictData dictData)
    {
        startPage();
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        return getDataTable(list);
    }

    @Log(title = "字典数据", businessType = BusinessType.EXPORT)
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:dict:export",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDictData dictData)
    {
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        ExcelUtil<SysDictData> util = new ExcelUtil<SysDictData>(SysDictData.class);
        util.exportExcel(response, list, "字典数据");
    }

    /**
     * 查询字典数据详细
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:dict:query",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{dictCode}")
    public AjaxResult getInfo(@PathVariable Long dictCode)
    {
        return AjaxResult.success(dictDataService.selectDictDataById(dictCode));
    }

    /**
     * 根据字典类型查询字典数据信息
     */
    @GetMapping(value = "/type/{dictType}")
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult dictType(@PathVariable String dictType)
    {
        List<SysDictData> data = dictTypeService.selectDictDataByType(dictType);
        if (StringUtils.isNull(data))
        {
            data = new ArrayList<SysDictData>();
        }
        return AjaxResult.success(data);
    }

    /**
     * 新增字典类型
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:dict:add",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody dev.mesbase.system.dto.DictDataForm dictForm)
    {
        SysDictData dict=dictForm.toEntity();
        dict.setDictCode(null);
        dict.setCreateBy(getUsername());
        return toAjax(dictDataService.insertDictData(dict));
    }

    /**
     * 修改保存字典类型
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:dict:edit",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody dev.mesbase.system.dto.DictDataForm dictForm)
    {
        SysDictData dict=dictForm.toEntity();
        dict.setUpdateBy(getUsername());
        return toAjax(dictDataService.updateDictData(dict));
    }

    /**
     * 删除字典类型
     */
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:dict:remove",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictCodes}")
    public AjaxResult remove(@PathVariable Long[] dictCodes)
    {
        dictDataService.deleteDictDataByIds(dictCodes);
        return success();
    }
}
