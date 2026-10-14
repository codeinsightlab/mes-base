package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.ProductionIdentityVo;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/identity")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionIdentityController {
    private final ProductionIdentityService service;

    public ProductionIdentityController(ProductionIdentityService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesProductionExecutionPermissions.QUERY)
    public ProductionIdentityVo resolve(@RequestParam String code) {
        var result = service.resolve(code);
        return new ProductionIdentityVo(result.executionId().toString(), result.executionNo(), result.qrCode(),
                result.workorderId().toString(), result.workorderCode(), result.productId()
                .toString(), result.productCode(),
                result.productName(), result.routingId().toString(), result.routingCode(), result.routingVersion(),
                result.status(), result.targetQty().toPlainString());
    }
}
