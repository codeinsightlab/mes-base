package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionInputService.Command;
import dev.mesbase.business.production.service.ProductionInputService.Result;
import dev.mesbase.business.integration.production.ProductionSupply.Stock;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.business.production.controller.vo.RoutingDetailVo;
import dev.mesbase.business.production.controller.ExecutionActionController.PathView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/production/input")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInputController {
    public static final String PERMISSION = "business:mes:material:issue:update";
    private final ProductionInputSupplyService supply;
    private final ProductionInputService service;

    public ProductionInputController(ProductionInputSupplyService supply, ProductionInputService service) {
        this.supply = supply;
        this.service = service;
    }

    public record StockView(String reference, String materialId, String materialCode, String lotCode,
                            String warehouseId,
                            String warehouseCode, String binCode, String unitId, String unitCode, String availableQty,
                            LocalDateTime observedAt) {
    }

    public record OrderView(String workorderId, String workorderCode, String productCode, String productName,
                            String workCenter,
                            String plannedQty, String outputUnitCode, String status, List<String> allowedWarehouses) {
    }

    public record SupplyView(String sourceMode, List<StockView> stocks, List<OrderView> orders, long total, int offset,
                             int limit) {
    }

    public record CalculationView(String sourceMode, OrderView order, StockView stock, String outputQty,
                                  String issueQty, String calculationKey) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public SupplyView query(@RequestParam String code, @RequestParam(defaultValue = "0") int offset,
                            @RequestParam(defaultValue = "20") int limit) {
        var c = supply.query(code, null, offset, limit);
        return new SupplyView(c.sourceMode(), c.stocks().stream().map(ProductionInputController::stock)
                .toList(), c.orders().stream().map(ProductionInputController::order)
                .toList(), c.total(), c.offset(), c.limit());
    }

    @GetMapping("/calculate")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public CalculationView calculate(@RequestParam String code, @RequestParam Long workorderId,
                                     @RequestParam String stockReference, @RequestParam BigDecimal outputQty) {
        var c = supply.calculate(code, workorderId, stockReference, outputQty);
        return new CalculationView(c.sourceMode(), order(c.order()), stock(c.stock()), c.outputQty()
                .toPlainString(), c.issueQty().toPlainString(), ProductionInputSupplyService.calculationKey(c));
    }

    public record PreviewView(RoutingDetailVo definition, PathView actual) {
    }

    @GetMapping("/preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public PreviewView preview(@RequestParam String code, @RequestParam Long workorderId) {
        var p = service.preview(code, workorderId);
        return new PreviewView(RoutingController.detailView(p.definition()), p.actual() == null ? null : ExecutionActionController.toView(p.actual()));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public Result submit(@RequestBody Command request) {
        return service.submit(request);
    }

    private static StockView stock(Stock s) {
        return new StockView(s.reference(), s.materialId().toString(), s.materialCode(), s.lotCode(), s.warehouseId()
                .toString(), s.warehouseCode(), s.binCode(), s.unitId().toString(), s.unitCode(), s.availableQty()
                .toPlainString(), s.observedAt());
    }

    private static OrderView order(Order o) {
        return new OrderView(o.workorderId()
                .toString(), o.workorderCode(), o.productCode(), o.productName(), o.workCenter(), o.plannedQty()
                .toPlainString(), o.outputUnitCode(), o.status(), o.allowedWarehouses().stream().map(Object::toString)
                .toList());
    }
}
