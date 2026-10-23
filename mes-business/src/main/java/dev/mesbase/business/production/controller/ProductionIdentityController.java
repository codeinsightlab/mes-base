package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.ProductionIdentityVo;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/identity")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionIdentityController {
    private final ProductionIdentityService service;

    public ProductionIdentityController(ProductionIdentityService service) {
        this.service = service;
    }

    @GetMapping("/{executionId}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesProductionExecutionPermissions.QUERY)
    public ProductionIdentityVo read(@PathVariable Long executionId) {
        return BeanConversions.convert(service.read(executionId), ProductionIdentityVo.class);
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesProductionExecutionPermissions.QUERY)
    public ProductionIdentityVo resolve(@RequestParam String code) {
        return BeanConversions.convert(service.resolve(code), ProductionIdentityVo.class);
    }
}
