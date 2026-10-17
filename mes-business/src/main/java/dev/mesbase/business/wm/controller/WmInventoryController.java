package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.controller.vo.WmInventoryPageVo;
import dev.mesbase.business.wm.controller.vo.WmInventoryVo;
import dev.mesbase.business.wm.domain.WmInventoryCreateCommand;
import dev.mesbase.business.wm.domain.WmInventoryItem;
import dev.mesbase.business.wm.domain.WmInventoryPage;
import dev.mesbase.business.wm.domain.WmInventoryQueryDto;
import dev.mesbase.business.wm.domain.WmInventoryUpdateCommand;
import dev.mesbase.business.wm.service.IWmInventoryService;
import dev.mesbase.business.wm.sync.WmInventorySyncResult;
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
 * 库存管理 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/wm/wm-inventory")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WmInventoryController {
    private final IWmInventoryService service;

    public WmInventoryController(IWmInventoryService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmInventoryPermissions.LIST)
    public WmInventoryPageVo list(@Valid @ModelAttribute WmInventoryQueryDto query) {
        WmInventoryPage page = service.selectPage(query);
        return new WmInventoryPageVo(
                page.items().stream().map(WmInventoryController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmInventoryPermissions.QUERY)
    public WmInventoryVo detail(@PathVariable Long id) {
        return toView(service.selectDetail(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmInventoryPermissions.CREATE)
    public WmInventoryVo create(@Valid @RequestBody WmInventoryCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmInventoryPermissions.UPDATE)
    public WmInventoryVo update(@PathVariable Long id,
                                @Valid @RequestBody WmInventoryUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmInventoryPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmInventoryPermissions.SYNC)
    public WmInventorySyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static WmInventoryVo toView(WmInventoryItem item) {
        var row = item.inventory();
        return new WmInventoryVo(
                row.getId() == null ? null : row.getId().toString(),

                row.getMaterialId() == null ? null : row.getMaterialId().toString(),
                row.getWarehouseId() == null ? null : row.getWarehouseId().toString(),
                item.materialName(),
                item.warehouseName(),
                item.warehouseSource(),
                row.getQuantity() == null ? null : row.getQuantity().toString(),
                row.getAvailableQuantity() == null ? null : row.getAvailableQuantity().toString(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(), row.getBatchNo(), row.getBinCode()
        );
    }
}

