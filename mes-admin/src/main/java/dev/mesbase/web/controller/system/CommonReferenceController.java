package dev.mesbase.web.controller.system;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.web.view.dict.SysDictDataAssembler;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shared authenticated reads; configuration service retains its key-specific authorization.
 */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class CommonReferenceController {
    private final ISysConfigService configs;
    private final ISysDictTypeService dictionaries;

    public CommonReferenceController(ISysConfigService configs, ISysDictTypeService dictionaries) {
        this.configs = configs;
        this.dictionaries = dictionaries;
    }

    @GetMapping("/api/common/config/{key}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED,
            permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult config(@PathVariable String key) {
        return AjaxResult.success((Object) configs.selectClientConfigByKey(key));
    }

    @GetMapping("/api/common/dict/{type}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED,
            permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult dictionary(@PathVariable String type) {
        var data = dictionaries.selectDictDataByType(type);
        return AjaxResult.success(SysDictDataAssembler.toResults(data == null ? List.of() : data));
    }
}
