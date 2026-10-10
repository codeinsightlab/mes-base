// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.acceptance.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import dev.mesbase.modules.acceptance.controller.vo.PersonPageVo;
import dev.mesbase.modules.acceptance.controller.vo.PersonVo;
import dev.mesbase.modules.acceptance.domain.PersonCreateCommand;
import dev.mesbase.modules.acceptance.domain.PersonEntity;
import dev.mesbase.modules.acceptance.domain.PersonPage;
import dev.mesbase.modules.acceptance.domain.PersonQueryDto;
import dev.mesbase.modules.acceptance.domain.PersonUpdateCommand;
import dev.mesbase.modules.acceptance.service.IPersonService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

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
 * 人员 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/acceptance/person")
@ConditionalOnProperty(name = "mes.codegen.person-review.enabled", havingValue = "true")
public class PersonController {
    private final IPersonService service;

    public PersonController(IPersonService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonPermissions.READ)
    public PersonPageVo list(@Valid @ModelAttribute PersonQueryDto query) {
        PersonPage page = service.selectPage(query);
        return new PersonPageVo(
                page.items().stream().map(PersonController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonPermissions.READ)
    public PersonVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonPermissions.CREATE)
    public PersonVo create(@Valid @RequestBody PersonCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonPermissions.UPDATE)
    public PersonVo update(@PathVariable Long id,
                           @Valid @RequestBody PersonUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PersonPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    private static PersonVo toView(PersonEntity row) {
        return new PersonVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getName()
        );
    }
}

