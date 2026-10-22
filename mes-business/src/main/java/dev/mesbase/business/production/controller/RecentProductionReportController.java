package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.ProductionReportViews;
import dev.mesbase.business.production.service.RecentProductionReportQueryService;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class RecentProductionReportController {
    private final RecentProductionReportQueryService service;

    public RecentProductionReportController(RecentProductionReportQueryService service) {
        this.service = service;
    }

    public record Row(String id, String executionId, String executionNo, String workorderId, String workorderCode,
                      String source, String productCode, String productName, String unitName, String routingNodeId,
                      String operationName, String recordType, String quantityDelta, String operatorUserId,
                      String operatorName, LocalDateTime reportTime, LocalDateTime createTime) {
    }

    @GetMapping("/api/production/reports/recent")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.DETAIL)
    public List<Row> recent() {
        var result = service.recent();
        return result.items().stream().map(r -> {
            var user = result.users().get(r.operatorUserId());
            return new Row(ProductionReportViews.id(r.id()), ProductionReportViews.id(r.executionId()),
                    r.executionNo(), ProductionReportViews.id(r.workorderId()), r.workorderCode(), r.source(),
                    r.productCode(), r.productName(), r.unitName(), ProductionReportViews.id(r.routingNodeId()),
                    r.operationName(), r.recordType(), ProductionReportViews.decimal(r.quantityDelta()),
                    ProductionReportViews.id(r.operatorUserId()), Optional.ofNullable(user)
                    .map(nullableValue -> nullableValue.name()).orElse(null),
                    r.reportTime(), r.createTime());
        }).toList();
    }
}
