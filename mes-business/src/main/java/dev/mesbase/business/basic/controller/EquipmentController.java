// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.controller.vo.EquipmentPageVo;
import dev.mesbase.business.basic.controller.vo.EquipmentVo;
import dev.mesbase.business.basic.domain.EquipmentCreateCommand;
import dev.mesbase.business.basic.domain.EquipmentEntity;
import dev.mesbase.business.basic.domain.EquipmentPage;
import dev.mesbase.business.basic.domain.EquipmentQueryDto;
import dev.mesbase.business.basic.domain.EquipmentUpdateCommand;
import dev.mesbase.business.basic.service.IEquipmentService;
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
 * 设备 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/equipment")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class EquipmentController {
    private final IEquipmentService service;

    public EquipmentController(IEquipmentService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = EquipmentPermissions.READ)
    public EquipmentPageVo list(@Valid @ModelAttribute EquipmentQueryDto query) {
        EquipmentPage page = service.selectPage(query);
        return new EquipmentPageVo(
                page.items().stream().map(EquipmentController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = EquipmentPermissions.READ)
    public EquipmentVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = EquipmentPermissions.CREATE)
    public EquipmentVo create(@Valid @RequestBody EquipmentCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = EquipmentPermissions.UPDATE)
    public EquipmentVo update(@PathVariable Long id,
            @Valid @RequestBody EquipmentUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = EquipmentPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    private static EquipmentVo toView(EquipmentEntity row) {
        return new EquipmentVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getCode(),
                row.getName(),
                row.getModel(),
                row.getWorkstationId() == null ? null : row.getWorkstationId().toString(),
                row.getStatus(),
                row.getRemark()
        );
    }
}

