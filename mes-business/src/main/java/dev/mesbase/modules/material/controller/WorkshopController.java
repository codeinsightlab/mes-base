// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.controller.vo.WorkshopPageVo;
import dev.mesbase.modules.material.controller.vo.WorkshopVo;
import dev.mesbase.modules.material.domain.WorkshopCreateCommand;
import dev.mesbase.modules.material.domain.WorkshopEntity;
import dev.mesbase.modules.material.domain.WorkshopPage;
import dev.mesbase.modules.material.domain.WorkshopQueryDto;
import dev.mesbase.modules.material.domain.WorkshopUpdateCommand;
import dev.mesbase.modules.material.service.IWorkshopService;
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
 * 车间 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/material/workshop")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkshopController {
    private final IWorkshopService service;

    public WorkshopController(IWorkshopService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.READ)
    public WorkshopPageVo list(@Valid @ModelAttribute WorkshopQueryDto query) {
        WorkshopPage page = service.selectPage(query);
        return new WorkshopPageVo(
                page.items().stream().map(WorkshopController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.READ)
    public WorkshopVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.CREATE)
    public WorkshopVo create(@Valid @RequestBody WorkshopCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.UPDATE)
    public WorkshopVo update(@PathVariable Long id,
                             @Valid @RequestBody WorkshopUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    private static WorkshopVo toView(WorkshopEntity row) {
        return new WorkshopVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getCode(),
                row.getName(),
                row.getStatus(),
                row.getRemark()
        );
    }
}

