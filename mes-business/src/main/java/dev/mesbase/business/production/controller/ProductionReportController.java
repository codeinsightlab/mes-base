package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.ProductionReportService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.business.production.domain.ProductionReportModels.Supplement;
import dev.mesbase.business.production.domain.ProductionReportModels.Adjustment;
import dev.mesbase.business.production.domain.ProductionReportModels.VoidOriginal;
import dev.mesbase.business.production.controller.vo.ProductionReportViews;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Ledger;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Detail;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Page;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Execution;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.Summary;
import dev.mesbase.business.production.controller.vo.ProductionReportViews.HistoricalUser;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/production")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReportController {
    private final ProductionReportService reports;
    private final ProductionQuantityService quantities;
    private final ReportUserReferenceService users;

    public ProductionReportController(ProductionReportService reports, ProductionQuantityService quantities,
                                      ReportUserReferenceService users) {
        this.reports = reports;
        this.quantities = quantities;
        this.users = users;
    }

    @GetMapping("/report-quantities/executions/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.QUANTITY)
    public Execution quantities(@PathVariable Long id) {
        return ProductionReportViews.execution(quantities.getExecutionQuantities(id));
    }

    @GetMapping("/report-quantities/workorders/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.QUANTITY)
    public Page<Summary> workorder(@PathVariable Long id, @RequestParam(defaultValue = "0") int offset,
                                   @RequestParam(defaultValue = "20") int limit) {
        var p = quantities.getWorkorderQuantities(id, offset, limit);
        return new Page<>(p.items().stream().map(ProductionReportViews::summary)
                .toList(), p.total(), p.offset(), p.limit());
    }

    @GetMapping("/reports")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.DETAIL)
    public Page<Ledger> page(@RequestParam Long executionId, @RequestParam Long routingNodeId,
                             @RequestParam(defaultValue = "0") int offset,
                             @RequestParam(defaultValue = "20") int limit) {
        var p = reports.page(executionId, routingNodeId, offset, limit);
        return new Page<>(p.items().stream().map(row -> ProductionReportViews.ledger(row, p.userNames()))
                .toList(), p.total(), p.offset(), p.limit());
    }

    @GetMapping("/reports/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.DETAIL)
    public Detail detail(@PathVariable Long id) {
        var d = reports.getDetail(id);
        return new Detail(ProductionReportViews.ledger(d.record(), d.userNames()), ProductionReportViews.decimal(d.originalEffectiveQty()));
    }

    @GetMapping("/reports/{id}/adjustments")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.DETAIL)
    public Page<Ledger> history(@PathVariable Long id, @RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "20") int limit) {
        var p = reports.adjustments(id, offset, limit);
        return new Page<>(p.items().stream().map(row -> ProductionReportViews.ledger(row, p.userNames()))
                .toList(), p.total(), p.offset(), p.limit());
    }

    @PostMapping("/reports/supplements")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.SUPPLEMENT)
    public Ledger supplement(@Valid @RequestBody Supplement command) {
        return ProductionReportViews.ledger(reports.supplement(command));
    }

    @PostMapping("/reports/{id}/adjustments")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.ADJUST)
    public Ledger adjust(@PathVariable Long id, @Valid @RequestBody Adjustment command) {
        return ProductionReportViews.ledger(reports.adjust(id, command));
    }

    @PostMapping("/reports/{id}/void")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.VOID)
    public Ledger voidOriginal(@PathVariable Long id, @Valid @RequestBody VoidOriginal command) {
        return ProductionReportViews.ledger(reports.voidOriginalReport(id, command));
    }

    @GetMapping("/report-operators")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.SUPPLEMENT)
    public Page<HistoricalUser> users(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "0") int offset,
                                      @RequestParam(defaultValue = "20") int limit) {
        var p = users.page(keyword, offset, limit);
        return new Page<>(p.items().stream().map(u -> new HistoricalUser(ProductionReportViews.id(u.id()), u.name()))
                .toList(), p.total(), p.offset(), p.limit());
    }
}
