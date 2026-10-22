package dev.mesbase.business.basic.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.basic.controller.vo.SafetyStockPageVo;
import dev.mesbase.business.basic.controller.vo.SafetyStockVo;
import dev.mesbase.business.basic.domain.SafetyStockCreateCommand;
import dev.mesbase.business.basic.domain.SafetyStockEntity;
import dev.mesbase.business.basic.domain.SafetyStockQueryDto;
import dev.mesbase.business.basic.domain.SafetyStockUpdateCommand;
import dev.mesbase.business.basic.service.ISafetyStockService;
import dev.mesbase.business.basic.sync.SafetyStockSyncResult;
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
 * 安全库存配置 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/safety-stock")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class SafetyStockController {
    private final ISafetyStockService service;

    public SafetyStockController(ISafetyStockService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.LIST)
    public SafetyStockPageVo list(@Valid @ModelAttribute SafetyStockQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), SafetyStockPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.QUERY)
    public SafetyStockVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.CREATE)
    public SafetyStockVo create(@Valid @RequestBody SafetyStockCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.UPDATE)
    public SafetyStockVo update(@PathVariable Long id,
                                @Valid @RequestBody SafetyStockUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.SYNC)
    public SafetyStockSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static SafetyStockVo toView(SafetyStockEntity row) {
        return BeanConversions.convert(row, SafetyStockVo.class);
    }
}

