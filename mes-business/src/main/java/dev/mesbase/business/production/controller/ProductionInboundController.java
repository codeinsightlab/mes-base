package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.ProductionInboundService;
import dev.mesbase.business.wm.controller.StockDocumentController.Detail;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInboundController {
    private final ProductionInboundService service;

    public ProductionInboundController(ProductionInboundService service) {
        this.service = service;
    }

    @PostMapping("/api/production/executions/{id}/inbound")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:document:post")
    public Detail receive(@PathVariable Long id, @Valid @RequestBody ProductionInboundService.Command command) {
        return BeanConversions.convert(service.receive(id, command), Detail.class);
    }
}
