package dev.mesbase.business.production.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.business.production.service.WorkOrderProgressQueryService;
import dev.mesbase.business.production.domain.WorkOrderProgress.Query;
import dev.mesbase.business.production.domain.WorkOrderProgress.Progress;
import java.util.List;
import java.time.LocalDateTime;
import static dev.mesbase.business.production.controller.vo.ProductionReportViews.decimal;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderProgressController {
    private final WorkOrderProgressQueryService service;

    public WorkOrderProgressController(WorkOrderProgressQueryService service) {
        this.service = service;
    }

    public record View(String id, String code, String source, String status, String productCode,
                       String productName, String unitName, String plannedQty, String completedQty,
                       String goodOutputGapQty, String scrappedQty, String pendingCompletionQty,
                       String qualityUntreatedQty, String qualityWaitingReworkQty, String qualityReworkingQty,
                       String qualityUnverifiedQty, String completionPercent, long completedExecutions,
                       LocalDateTime lastCompletedAt, String issue, LocalDateTime createTime, String qualityTransferredQty) { }

    public record Page(List<View> items, long total, int offset, int limit) { }

    @GetMapping("/api/production/workorder-progress")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:list")
    public Page page(@RequestParam(required = false) String workorderCode,
                     @RequestParam(required = false) String productCode, @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "5") int limit) {
        return BeanConversions.convertPage(service.page(new Query(workorderCode, productCode, status, offset, limit)), Page.class);
    }
}
