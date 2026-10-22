package dev.mesbase.business.production.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.production.controller.vo.OperationPageVo;
import dev.mesbase.business.production.controller.vo.OperationVo;
import dev.mesbase.business.production.domain.OperationCreateCommand;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationQueryDto;
import dev.mesbase.business.production.domain.OperationUpdateCommand;
import dev.mesbase.business.production.service.IOperationService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import dev.mesbase.business.production.sync.ProductionSyncResult;
import dev.mesbase.platform.execution.FactoryContext;
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
 * 工序 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/operation")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperationController {
    private final IOperationService service;

    public OperationController(IOperationService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = OperationPermissions.LIST)
    public OperationPageVo list(@Valid @ModelAttribute OperationQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), OperationPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = OperationPermissions.QUERY)
    public OperationVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = OperationPermissions.CREATE)
    public OperationVo create(@Valid @RequestBody OperationCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = OperationPermissions.UPDATE)
    public OperationVo update(@PathVariable Long id,
                              @Valid @RequestBody OperationUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = OperationPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = OperationPermissions.SYNC)
    public ProductionSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static OperationVo toView(OperationEntity row) {
        return BeanConversions.convert(row, OperationVo.class);
    }
}

