// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.controller.vo.MaterialPageVo;
import dev.mesbase.business.basic.controller.vo.MaterialVo;
import dev.mesbase.business.basic.domain.MaterialCreateCommand;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialPage;
import dev.mesbase.business.basic.domain.MaterialQueryDto;
import dev.mesbase.business.basic.domain.MaterialUpdateCommand;
import dev.mesbase.business.basic.service.IMaterialService;
import dev.mesbase.business.basic.sync.MaterialSyncResult;
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
 * 物料产品 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/material")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialController {
    private final IMaterialService service;

    public MaterialController(IMaterialService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialPermissions.LIST)
    public MaterialPageVo list(@Valid @ModelAttribute MaterialQueryDto query) {
        MaterialPage page = service.selectPage(query);
        return new MaterialPageVo(
                page.items().stream().map(MaterialController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialPermissions.QUERY)
    public MaterialVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialPermissions.CREATE)
    public MaterialVo create(@Valid @RequestBody MaterialCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialPermissions.UPDATE)
    public MaterialVo update(@PathVariable Long id,
            @Valid @RequestBody MaterialUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialPermissions.SYNC)
    public MaterialSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static MaterialVo toView(MaterialEntity row) {
        return new MaterialVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getCode(),
                row.getName(),
                row.getSpecification(),
                row.getCategoryId() == null ? null : row.getCategoryId().toString(),
                row.getUnitId() == null ? null : row.getUnitId().toString(),
                row.getMaterialType(),
                row.getStatus(),
                row.getExternalId(),
                row.getExternalCode()
        );
    }
}

