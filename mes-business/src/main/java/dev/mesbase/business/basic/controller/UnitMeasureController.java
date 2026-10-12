// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.controller.vo.UnitMeasurePageVo;
import dev.mesbase.business.basic.controller.vo.UnitMeasureVo;
import dev.mesbase.business.basic.domain.UnitMeasureCreateCommand;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasurePage;
import dev.mesbase.business.basic.domain.UnitMeasureQueryDto;
import dev.mesbase.business.basic.domain.UnitMeasureUpdateCommand;
import dev.mesbase.business.basic.service.IUnitMeasureService;
import dev.mesbase.business.basic.sync.UnitMeasureSyncResult;
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
 * 计量单位 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/unit-measure")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class UnitMeasureController {
    private final IUnitMeasureService service;

    public UnitMeasureController(IUnitMeasureService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.LIST)
    public UnitMeasurePageVo list(@Valid @ModelAttribute UnitMeasureQueryDto query) {
        UnitMeasurePage page = service.selectPage(query);
        return new UnitMeasurePageVo(
                page.items().stream().map(UnitMeasureController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.QUERY)
    public UnitMeasureVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.CREATE)
    public UnitMeasureVo create(@Valid @RequestBody UnitMeasureCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.UPDATE)
    public UnitMeasureVo update(@PathVariable Long id,
                                @Valid @RequestBody UnitMeasureUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.SYNC)
    public UnitMeasureSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static UnitMeasureVo toView(UnitMeasureEntity row) {
        return new UnitMeasureVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getCode(),
                row.getName(),
                row.getSymbol(),
                row.getStatus()
        );
    }
}

