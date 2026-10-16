package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.ProductionIdentityVo;
import dev.mesbase.business.production.controller.vo.ProductionReportViews;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Execution;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Ledger;
import dev.mesbase.business.production.controller.ExecutionActionController.PathView;
import dev.mesbase.business.production.domain.ProductionReportSubmitModels.Command;
import dev.mesbase.business.production.service.ProductionReportSubmitService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/production/reporting")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReportingController {
    public static final String PERMISSION = "business:production:report:submit";
    private final ProductionReportSubmitService service;

    public ProductionReportingController(ProductionReportSubmitService service) {
        this.service = service;
    }

    public record SubmitRequest(@NotNull Long executionId, @NotNull Long routingNodeId,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal reportQty,
                                Long workstationId,
                                @NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId) {
    }

    public record Candidate(String routingNodeId, String status, String reason) {
    }

    public record Context(ProductionIdentityVo identity, Execution quantities, PathView path,
                          List<Candidate> candidates) {
    }

    @GetMapping("/context")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public Context context(@RequestParam String code) {
        var c = service.context(code);
        var i = c.identity();
        var identity = new ProductionIdentityVo(i.executionId().toString(), i.executionNo(), i.qrCode(), i.workorderId()
                .toString(),
                i.workorderCode(), i.productId().toString(), i.productCode(), i.productName(), i.routingId().toString(),
                i.routingCode(), i.routingVersion(), i.status(), i.targetQty().toPlainString());
        return new Context(identity, ProductionReportViews.execution(c.quantities()), ExecutionActionController.toView(c.path()),
                c.candidates().stream().map(n -> new Candidate(n.routingNodeId().toString(), n.status(), n.reason()))
                        .toList());
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public Ledger submit(@RequestBody @Valid SubmitRequest request) {
        return ProductionReportViews.ledger(service.submit(new Command(request.executionId(), request.routingNodeId(),
                request.reportQty(), request.workstationId(), request.requestId())));
    }
}
