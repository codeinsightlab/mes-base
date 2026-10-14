package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.ProductionBatchEntity;
import dev.mesbase.business.production.controller.vo.RoutingDetailVo;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderController {
    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    public record OrderView(String id, String code, String productId, BigDecimal plannedQty, String routingId,
                            String status, LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                            String externalId, String externalCode, String remark, int revision,
                            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    public record RoutingView(String id, String code, String name, int version, String status) {
    }

    public record NodeView(String routingNodeId, int executionOrder) {
    }

    public record BatchView(String id, String workorderId, String batchNo, BigDecimal plannedQty,
                            String status, String routeStatus, LocalDateTime lockedAt, int revision, String remark,
                            List<NodeView> path) {
    }

    public record DetailView(OrderView workorder, String productCode, String productName, RoutingView routing,
                             List<BatchView> batches, AllocationView allocation) {
    }

    public record SummaryView(OrderView workorder, int batchCount, String productCode, String productName,
                              String routingCode, Integer routingVersion, AllocationView allocation) {
    }

    public record AllocationView(String workorderId, String plannedQty, String allocatedTargetQty,
                                 String remainingTargetQty, String excessTargetQty, Long executionCount) {
    }

    public record PageView(List<SummaryView> items, long total, int offset, int limit) {
    }

    @GetMapping("/workorder")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:list")
    public PageView list(@RequestParam(required = false) String code, @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
        var p = service.list(code, status, offset, limit);
        return new PageView(p.items().stream()
                .map(s -> new SummaryView(order(s.workorder()), s.batchCount(), s.productCode(), s.productName(), s.routingCode(), s.routingVersion(), allocation(s.allocation())))
                .toList(), p.total(), p.offset(), p.limit());
    }

    @GetMapping("/workorder/references/routings")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public List<RoutingView> routings(@RequestParam Long productId, @RequestParam(required = false) String search) {
        return service.applicableRoutings(productId, search).stream()
                .map(r -> new RoutingView(id(r.getId()), r.getCode(), r.getName(), r.getVersion(), r.getStatus()))
                .toList();
    }

    @GetMapping("/workorder/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public DetailView detail(@PathVariable Long id) {
        return view(service.detail(id));
    }

    @GetMapping("/workorder/{id}/allocation")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public AllocationView allocation(@PathVariable Long id) {
        return allocation(service.allocation(id));
    }

    @GetMapping("/workorder/{id}/graph")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public RoutingDetailVo graph(@PathVariable Long id) {
        return RoutingController.detailView(service.graph(id));
    }

    @PostMapping("/workorder")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:create")
    public DetailView create(@Valid @RequestBody WorkOrderSaveRequest input) {
        return view(service.create(input.toCommand()));
    }

    @PutMapping("/workorder/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:update")
    public DetailView update(@PathVariable Long id, @Valid @RequestBody WorkOrderSaveRequest input) {
        return view(service.update(id, input.toCommand()));
    }

    @PostMapping("/workorder/{id}/actions/release")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:release")
    public DetailView release(@PathVariable Long id, @Valid @RequestBody WorkOrderModels.Revision input) {
        return view(service.release(id, input.revision()));
    }

    @DeleteMapping("/workorder/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:delete")
    public void delete(@PathVariable Long id, @Valid @RequestBody WorkOrderModels.Revision input) {
        service.delete(id, input.revision());
    }

    @GetMapping("/batch/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "batch:query")
    public BatchView batch(@PathVariable Long id) {
        return batch(service.batchDetail(id));
    }

    @PutMapping("/batch/{id}/path")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "batch:selectRoute")
    public BatchView path(@PathVariable Long id, @Valid @RequestBody WorkOrderModels.PathSelection input) {
        return batch(service.selectPath(id, input));
    }

    private static AllocationView allocation(WorkOrderModels.Allocation a) {
        return new AllocationView(id(a.workorderId()), a.plannedQty().toPlainString(), a.allocatedTargetQty()
                .toPlainString(),
                a.remainingTargetQty().toPlainString(), a.excessTargetQty().toPlainString(), a.executionCount());
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static OrderView order(WorkorderEntity w) {
        return new OrderView(id(w.getId()), w.getCode(), id(w.getProductId()), w.getPlannedQty(), id(w.getRoutingId()), w.getStatus(),
                w.getPlannedStartAt(), w.getPlannedEndAt(), w.getExternalId(), w.getExternalCode(), w.getRemark(), w.getRevision(), w.getCreateTime(), w.getUpdateTime());
    }

    private static BatchView batch(WorkOrderModels.BatchDetail detail) {
        ProductionBatchEntity b = detail.batch();
        return new BatchView(id(b.getId()), id(b.getWorkorderId()), b.getBatchNo(), b.getPlannedQty(), b.getStatus(), b.getRouteStatus(), b.getLockedAt(), b.getRevision(), b.getRemark(),
                detail.path().stream().map(n -> new NodeView(id(n.getRoutingNodeId()), n.getExecutionOrder()))
                        .toList());
    }

    private static DetailView view(WorkOrderModels.Detail detail) {
        RoutingEntity r = detail.routing();
        var routing = new RoutingView(id(r.getId()), r.getCode(), r.getName(), r.getVersion(), r.getStatus());
        return new DetailView(order(detail.workorder()), detail.productCode(), detail.productName(), routing, detail.batches()
                .stream().map(WorkOrderController::batch).toList(), allocation(detail.allocation()));
    }
}
