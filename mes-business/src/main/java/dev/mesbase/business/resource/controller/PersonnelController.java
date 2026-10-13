// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.controller;

import dev.mesbase.business.resource.controller.vo.PersonnelPageVo;
import dev.mesbase.business.resource.controller.vo.PersonnelVo;
import dev.mesbase.business.resource.domain.PersonnelCreateCommand;
import dev.mesbase.business.resource.domain.PersonnelEntity;
import dev.mesbase.business.resource.domain.PersonnelPage;
import dev.mesbase.business.resource.domain.PersonnelQueryDto;
import dev.mesbase.business.resource.domain.PersonnelUpdateCommand;
import dev.mesbase.business.resource.service.IPersonnelService;
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
 * 生产人员 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/resource/personnel")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PersonnelController {
    private final IPersonnelService service;

    public PersonnelController(IPersonnelService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonnelPermissions.READ)
    public PersonnelPageVo list(@Valid @ModelAttribute PersonnelQueryDto query) {
        PersonnelPage page = service.selectPage(query);
        return new PersonnelPageVo(
                page.items().stream().map(PersonnelController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonnelPermissions.READ)
    public PersonnelVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonnelPermissions.CREATE)
    public PersonnelVo create(@Valid @RequestBody PersonnelCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonnelPermissions.UPDATE)
    public PersonnelVo update(@PathVariable Long id,
            @Valid @RequestBody PersonnelUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonnelPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    private static PersonnelVo toView(PersonnelEntity row) {
        return new PersonnelVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getCode(),
                row.getName(),
                row.getUserId() == null ? null : row.getUserId().toString(),
                row.getStatus()
        );
    }
}

