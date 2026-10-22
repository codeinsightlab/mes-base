package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.controller.vo.WmWarehousePageVo;
import dev.mesbase.business.wm.controller.vo.WmWarehouseVo;
import dev.mesbase.business.wm.domain.WmWarehouseCreateCommand;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseQueryDto;
import dev.mesbase.business.wm.domain.WmWarehouseUpdateCommand;
import dev.mesbase.business.wm.service.IWmWarehouseService;
import dev.mesbase.business.wm.service.WarehouseQueryService;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import java.util.Map;
import dev.mesbase.business.wm.sync.WmWarehouseSyncResult;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库管理 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/wm/wm-warehouse")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WmWarehouseController {
    private final IWmWarehouseService service;

    private final WarehouseQueryService queries;

    public WmWarehouseController(IWmWarehouseService service, WarehouseQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmWarehousePermissions.LIST)
    public WmWarehousePageVo list(@Valid @ModelAttribute WmWarehouseQueryDto query) {
        var result = queries.page(query);
        return BeanConversions.convertPage(result.records(), row -> toView(row, result.users()), WmWarehousePageVo::new);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmWarehousePermissions.QUERY)
    public WmWarehouseVo detail(@PathVariable Long id) {
        var result = queries.detail(id);
        return toView(result.record(), result.users());
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmWarehousePermissions.CREATE)
    public WmWarehouseVo create(@Valid @RequestBody WmWarehouseCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmWarehousePermissions.UPDATE)
    public WmWarehouseVo update(@PathVariable Long id,
                                @Valid @RequestBody WmWarehouseUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmWarehousePermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmWarehousePermissions.SYNC)
    public WmWarehouseSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private record AuditNames(String createByName, String updateByName) {
    }

    private static WmWarehouseVo toView(WmWarehouseEntity row) {
        return BeanConversions.convert(row, WmWarehouseVo.class);
    }

    private static WmWarehouseVo toView(WmWarehouseEntity row, Map<Long, DirectoryLabel> users) {
        return BeanConversions.convertWith(row, new AuditNames(WarehouseQueryService.name(users, row.getCreateBy()),
                WarehouseQueryService.name(users, row.getUpdateBy())), WmWarehouseVo.class);
    }
}

