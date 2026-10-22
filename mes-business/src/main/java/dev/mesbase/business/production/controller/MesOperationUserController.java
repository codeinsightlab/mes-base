package dev.mesbase.business.production.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.production.controller.vo.MesOperationUserPageVo;
import dev.mesbase.business.production.controller.vo.MesOperationUserVo;
import dev.mesbase.business.production.domain.MesOperationUserCreateCommand;
import dev.mesbase.business.production.domain.MesOperationUserEntity;
import dev.mesbase.business.production.domain.MesOperationUserQueryDto;
import dev.mesbase.business.production.domain.MesOperationUserUpdateCommand;
import dev.mesbase.business.production.service.IMesOperationUserService;
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
 * 工序能力人员 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-operation-user")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesOperationUserController {
    private final IMesOperationUserService service;

    public MesOperationUserController(IMesOperationUserService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesOperationUserPermissions.LIST)
    public MesOperationUserPageVo list(@Valid @ModelAttribute MesOperationUserQueryDto query) {
        Page<MesOperationUserEntity> page = service.selectPage(query);
        return new MesOperationUserPageVo(
                page.stream().map(MesOperationUserController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesOperationUserPermissions.QUERY)
    public MesOperationUserVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesOperationUserPermissions.CREATE)
    public MesOperationUserVo create(@Valid @RequestBody MesOperationUserCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesOperationUserPermissions.UPDATE)
    public MesOperationUserVo update(@PathVariable Long id,
                                     @Valid @RequestBody MesOperationUserUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesOperationUserPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    public record BindUsers(@NotNull Long operationId, @NotEmpty @Size(max = 100) List<@NotNull Long> userIds) {
    }

    @PostMapping("/bind-users")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesOperationUserPermissions.CREATE)
    public void bindUsers(@RequestBody @Valid BindUsers input) {
        service.bindUsers(input.operationId(), input.userIds());
    }

    private static MesOperationUserVo toView(MesOperationUserEntity row) {
        return new MesOperationUserVo(
                Objects.toString(row.getId(), null),
                Objects.toString(row.getOperationId(), null),
                Objects.toString(row.getUserId(), null),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime()
        );
    }
}

