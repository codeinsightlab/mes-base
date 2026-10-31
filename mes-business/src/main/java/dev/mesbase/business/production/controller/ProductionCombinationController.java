package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.ProductionCombinationQr;
import dev.mesbase.business.production.domain.ProductionCombinationRules;
import dev.mesbase.business.production.service.ProductionCombinationService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/combinations")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionCombinationController {
    private final ProductionCombinationService service;

    public ProductionCombinationController(ProductionCombinationService service) {
        this.service = service;
    }

    public record Generate(@NotNull Long workorderId,
                           @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity) {
    }

    public record Item(String demandLineId, int lineNo, String materialId, String materialCode, String unitId,
                       String totalRequiredQty, List<String> batches) {
    }

    public record Detail(String id, String code, String workorderId, String workorderCode, String productId,
                         String outputUnitId, String demandRevision, String executionId, String executionStatus,
                         boolean closed, List<Item> items) {
    }

    public record Allocation(String demandLineId, String stockId, String materialId, String warehouseId,
                             String binCode, String batchNo, String unitId, String quantity) {
    }

    public record Generated(Detail combination, String requestedQty, String suggestedQty,
                            List<Allocation> allocations, String strategy) {
    }

    public record Calculation(Detail combination, String maximumQty, String outputQty,
                              List<Allocation> allocations, String calculationKey, List<WarehouseView> warehouses) {
    }

    public record WarehouseView(String id, String code, String name) {
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionInputController.PERMISSION)
    public Generated generate(@Valid @RequestBody Generate request) {
        return BeanConversions.convert(service.generate(request.workorderId(), request.quantity()), Generated.class);
    }

    @GetMapping("/resolve")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionInputController.PERMISSION)
    public Detail resolve(@RequestParam String code) {
        return BeanConversions.convert(service.detail(ProductionCombinationRules.parse(code, FactoryContext.required()
                .factoryId().value())), Detail.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionInputController.PERMISSION)
    public Detail detail(@PathVariable Long id) {
        return BeanConversions.convert(service.detail(id), Detail.class);
    }

    @GetMapping(value = "/{id}/qr", produces = "image/svg+xml")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionInputController.PERMISSION)
    public ResponseEntity<String> qr(@PathVariable Long id) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(ProductionCombinationQr.svg(service.detail(id).code()));
    }

    @GetMapping("/calculate")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionInputController.PERMISSION)
    public Calculation calculate(@RequestParam String code, @RequestParam(required = false) BigDecimal quantity) {
        return BeanConversions.convert(service.calculate(code, quantity), Calculation.class);
    }
}
