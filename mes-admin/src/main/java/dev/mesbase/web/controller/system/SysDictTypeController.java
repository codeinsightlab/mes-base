package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.dto.DictTypeForm;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.dict.SysDictTypeAssembler;
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
 * 数据字典信息
 * 
 * @author ruoyi
 */
@RestController
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
@RequestMapping("/api/system/dict/type")
public class SysDictTypeController extends BaseController
{
    @Autowired
    private ISysDictTypeService dictTypeService;

    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysDictType dictType)
    {
        startPage();
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        return PageResultMapper.map(list, SysDictTypeAssembler::toResult);
    }

    @Log(title = "字典类型", businessType = BusinessType.EXPORT)
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:export",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDictType dictType)
    {
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        ExcelUtil<SysDictType> util = new ExcelUtil<SysDictType>(SysDictType.class);
        util.exportExcel(response, list, "字典类型");
    }

    /**
     * 查询字典类型详细
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{dictId}")
    public AjaxResult getInfo(@PathVariable Long dictId)
    {
        return AjaxResult.success(SysDictTypeAssembler.toResult(dictTypeService.selectDictTypeById(dictId)));
    }

    /**
     * 新增字典类型
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DictTypeForm dictForm)
    {
        SysDictType dict=dictForm.toEntity();
        dict.setDictId(null);
        if (UserConstants.NOT_UNIQUE.equals(dictTypeService.checkDictTypeUnique(dict)))
        {
            return AjaxResult.error("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setCreateBy(getUsername());
        return toAjax(dictTypeService.insertDictType(dict));
    }

    /**
     * 修改字典类型
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DictTypeForm dictForm)
    {
        SysDictType dict=dictForm.toEntity();
        if (UserConstants.NOT_UNIQUE.equals(dictTypeService.checkDictTypeUnique(dict)))
        {
            return AjaxResult.error("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setUpdateBy(getUsername());
        return toAjax(dictTypeService.updateDictType(dict));
    }

    /**
     * 删除字典类型
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictIds}")
    public AjaxResult remove(@PathVariable Long[] dictIds)
    {
        dictTypeService.deleteDictTypeByIds(dictIds);
        return success();
    }

    /**
     * 刷新字典缓存
     */
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:dict:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典类型", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache()
    {
        dictTypeService.resetDictCache();
        return AjaxResult.success();
    }

    /**
     * 获取字典选择框列表
     */
    @GetMapping("/optionselect")
    @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult optionselect()
    {
        List<SysDictType> dictTypes = dictTypeService.selectDictTypeAll();
        return AjaxResult.success(SysDictTypeAssembler.toResults(dictTypes));
    }
}
