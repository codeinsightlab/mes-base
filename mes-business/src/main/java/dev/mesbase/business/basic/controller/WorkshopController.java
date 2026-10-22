package dev.mesbase.business.basic.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.basic.controller.vo.WorkshopPageVo;
import dev.mesbase.business.basic.controller.vo.WorkshopVo;
import dev.mesbase.business.basic.domain.WorkshopCreateCommand;
import dev.mesbase.business.basic.domain.WorkshopEntity;
import dev.mesbase.business.basic.domain.WorkshopQueryDto;
import dev.mesbase.business.basic.domain.WorkshopUpdateCommand;
import dev.mesbase.business.basic.service.IWorkshopService;
import dev.mesbase.business.basic.sync.WorkshopSyncResult;
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
 * 车间 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/workshop")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkshopController {
    private final IWorkshopService service;

    public WorkshopController(IWorkshopService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.LIST)
    public WorkshopPageVo list(@Valid @ModelAttribute WorkshopQueryDto query) {
        Page<WorkshopEntity> page = service.selectPage(query);
        return new WorkshopPageVo(
                page.stream().map(WorkshopController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.QUERY)
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

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkshopPermissions.SYNC)
    public WorkshopSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static WorkshopVo toView(WorkshopEntity row) {
        return new WorkshopVo(
                Objects.toString(row.getId(), null),

                row.getCode(),
                row.getName(),
                row.getStatus(),
                row.getRemark()
        );
    }
}

