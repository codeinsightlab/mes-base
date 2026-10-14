package dev.mesbase.business.md.controller;

import dev.mesbase.business.md.controller.vo.MdWorkshopPageVo;
import dev.mesbase.business.md.controller.vo.MdWorkshopVo;
import dev.mesbase.business.md.domain.MdWorkshopCreateCommand;
import dev.mesbase.business.md.domain.MdWorkshopEntity;
import dev.mesbase.business.md.domain.MdWorkshopPage;
import dev.mesbase.business.md.domain.MdWorkshopQueryDto;
import dev.mesbase.business.md.domain.MdWorkshopUpdateCommand;
import dev.mesbase.business.md.service.IMdWorkshopService;
import dev.mesbase.business.md.sync.MdWorkshopSyncResult;
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
@RequestMapping("/api/md/md-workshop")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdWorkshopController {
    private final IMdWorkshopService service;

    public MdWorkshopController(IMdWorkshopService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.LIST)
    public MdWorkshopPageVo list(@Valid @ModelAttribute MdWorkshopQueryDto query) {
        MdWorkshopPage page = service.selectPage(query);
        return new MdWorkshopPageVo(
                page.items().stream().map(MdWorkshopController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.QUERY)
    public MdWorkshopVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.CREATE)
    public MdWorkshopVo create(@Valid @RequestBody MdWorkshopCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.UPDATE)
    public MdWorkshopVo update(@PathVariable Long id,
                               @Valid @RequestBody MdWorkshopUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.SYNC)
    public MdWorkshopSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static MdWorkshopVo toView(MdWorkshopEntity row) {
        return new MdWorkshopVo(
                row.getWorkshopId() == null ? null : row.getWorkshopId().toString(),

                row.getWorkshopCode(),
                row.getWorkshopName(),
                row.getArea() == null ? null : row.getArea().toString(),
                row.getCharge(),
                row.getDeptId() == null ? null : row.getDeptId().toString(),
                row.getEnableFlag(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

