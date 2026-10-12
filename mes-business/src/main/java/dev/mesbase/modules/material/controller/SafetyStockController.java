// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.controller.vo.SafetyStockPageVo;
import dev.mesbase.modules.material.controller.vo.SafetyStockVo;
import dev.mesbase.modules.material.domain.SafetyStockCreateCommand;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockPage;
import dev.mesbase.modules.material.domain.SafetyStockQueryDto;
import dev.mesbase.modules.material.domain.SafetyStockUpdateCommand;
import dev.mesbase.modules.material.service.ISafetyStockService;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.modules.material.sync.SafetyStockSyncResult;
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
 * 安全库存配置 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/material/safety-stock")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class SafetyStockController {
    private final ISafetyStockService service;

    public SafetyStockController(ISafetyStockService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.LIST)
    public SafetyStockPageVo list(@Valid @ModelAttribute SafetyStockQueryDto query) {
        SafetyStockPage page = service.selectPage(query);
        return new SafetyStockPageVo(
                page.items().stream().map(SafetyStockController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.QUERY)
    public SafetyStockVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.CREATE)
    public SafetyStockVo create(@Valid @RequestBody SafetyStockCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.UPDATE)
    public SafetyStockVo update(@PathVariable Long id,
                                @Valid @RequestBody SafetyStockUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    private static SafetyStockVo toView(SafetyStockEntity row) {
        return new SafetyStockVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getMaterialId() == null ? null : row.getMaterialId().toString(),
                row.getMinQty() == null ? null : row.getMinQty().toString(),
                row.getMaxQty() == null ? null : row.getMaxQty().toString(),
                row.getStatus()
        );
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = SafetyStockPermissions.SYNC)
    public SafetyStockSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}
