package dev.mesbase.web.controller.system;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.enums.BusinessType;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.poi.ExcelUtil;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.dto.DictDataForm;
import dev.mesbase.system.service.ISysDictDataService;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.dict.SysDictDataAssembler;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
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
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
@RequestMapping("/api/system/dict/data")
public class SysDictDataController extends BaseController {
    @Autowired
    private ISysDictDataService dictDataService;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:dict:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/list")
    public TableDataInfo list(SysDictData dictData) {
        startPage();
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        return PageResultMapper.map(list, SysDictDataAssembler::toResult);
    }

    @Log(title = "字典数据", businessType = BusinessType.EXPORT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:dict:export", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDictData dictData) {
        List<SysDictData> list = dictDataService.selectDictDataList(dictData);
        ExcelUtil<SysDictData> util = new ExcelUtil<SysDictData>(SysDictData.class);
        util.exportExcel(response, list, "字典数据");
    }

    /**
     * 查询字典数据详细
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:dict:query", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping(value = "/{dictCode}")
    public AjaxResult getInfo(@PathVariable Long dictCode) {
        return AjaxResult.success(SysDictDataAssembler.toResult(dictDataService.selectDictDataById(dictCode)));
    }

    /**
     * 根据字典类型查询字典数据信息
     */
    @GetMapping(value = "/type/{dictType}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult dictType(@PathVariable String dictType) {
        List<SysDictData> data = dictTypeService.selectDictDataByType(dictType);
        if (StringUtils.isNull(data)) {
            data = new ArrayList<SysDictData>();
        }
        return AjaxResult.success(SysDictDataAssembler.toResults(data));
    }

    /**
     * 新增字典类型
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:dict:add", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody DictDataForm dictForm) {
        SysDictData dict = dictForm.toEntity();
        dict.setDictCode(null);
        dict.setCreateBy(getUsername());
        return toAjax(dictDataService.insertDictData(dict));
    }

    /**
     * 修改保存字典类型
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:dict:edit", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody DictDataForm dictForm) {
        SysDictData dict = dictForm.toEntity();
        dict.setUpdateBy(getUsername());
        return toAjax(dictDataService.updateDictData(dict));
    }

    /**
     * 删除字典类型
     */
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:dict:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictCodes}")
    public AjaxResult remove(@PathVariable Long[] dictCodes) {
        dictDataService.deleteDictDataByIds(dictCodes);
        return success();
    }
}
