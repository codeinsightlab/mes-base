package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Node;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Warehouse;
import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.quality.service.ProductionDefectService.Result;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import java.util.List;
import java.util.Map;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Names;

@RestController
@RequestMapping("/api/quality/defects")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionDefectController {
    private final ProductionDefectService service;

    public ProductionDefectController(ProductionDefectService service) {
        this.service = service;
    }

    public record Version(String id, String declarationId, Integer revisionNo, String recordAction,
                          String revisionReason, String executionId, String affectedNodeId, String discoveredNodeId,
                          String responsibleNodeId, String responsibleUserId, String targetNodeId,
                          String targetWarehouseId, String reportedBy, String recordedBy, String wasteCategory,
                          String defectReason, String responsibilityRemark, String destinationType,
                          String destinationRemark, String defectQty, LocalDateTime occurredAt,
                          LocalDateTime recordedAt, String discoveredNodeName, String affectedNodeName,
                          String responsibleNodeName, String targetNodeName, String targetWarehouseName,
                          String responsibleUserName, String reportedByName, String recordedByName) {
    }

    public record Page(List<Version> items, long total, int offset, int limit) {
    }

    public record Acknowledgement(Version record, boolean success, boolean externalExecuted, String executionMode,
                                  String externalReference) {
    }

    public record Total(String affectedNodeId, String effectiveDefectQty) {
    }

    public static Version view(MesProductionDefectEntity r) {
        return view(r, new Names(Map.of(), Map.of(), Map.of()));
    }

    public static Version view(MesProductionDefectEntity r, Names names) {
        return new Version(id(r.getId()), r.getDeclarationId(), r.getRevisionNo(), r.getRecordAction(), r.getRevisionReason(), id(r.getExecutionId()), id(r.getAffectedNodeId()), id(r.getDiscoveredNodeId()), id(r.getResponsibleNodeId()), id(r.getResponsibleUserId()), id(r.getTargetNodeId()), id(r.getTargetWarehouseId()), id(r.getReportedBy()), id(r.getRecordedBy()), r.getWasteCategory(), r.getDefectReason(), r.getResponsibilityRemark(), r.getDestinationType(), r.getDestinationRemark(), r.getDefectQty()
                .toPlainString(), r.getOccurredAt(), r.getRecordedAt(), label(names.nodes(), r.getDiscoveredNodeId()), label(names.nodes(), r.getAffectedNodeId()), label(names.nodes(), r.getResponsibleNodeId()), label(names.nodes(), r.getTargetNodeId()), label(names.warehouses(), r.getTargetWarehouseId()), label(names.users(), r.getResponsibleUserId()), label(names.users(), r.getReportedBy()), label(names.users(), r.getRecordedBy()));
    }

    private static String label(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    private static String id(Long id) {
        return id == null ? null : id.toString();
    }

    private static Acknowledgement result(Result r) {
        return new Acknowledgement(view(r.record()), r.erp().status() == Status.SUCCEEDED, r.erp()
                .executionMode() == Mode.CONNECTED, r.erp().executionMode().getValue(), r.erp()
                .externalReference());
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public Page page(@RequestParam(required = false) Long executionId, @RequestParam(required = false) Long workorderId,
                     @RequestParam(required = false) Long affectedNodeId, @RequestParam(defaultValue = "0") int offset,
                     @RequestParam(defaultValue = "20") int limit) {
        var p = service.page(executionId, workorderId, affectedNodeId, offset, limit);
        return new Page(p.items().stream().map(r -> view(r, p.names())).toList(), p.total(), offset, limit);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public Version detail(@PathVariable String id) {
        return view(service.latest(id));
    }

    @GetMapping("/{id}/history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public Page history(@PathVariable String id, @RequestParam(defaultValue = "0") int offset,
                        @RequestParam(defaultValue = "20") int limit) {
        var p = service.history(id, offset, limit);
        return new Page(p.items().stream().map(r -> view(r, p.names())).toList(), p.total(), offset, limit);
    }

    @GetMapping("/executions/{id}/totals")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public List<Total> totals(@PathVariable Long id) {
        return service.nodeTotals(id).stream()
                .map(t -> new Total(t.affectedNodeId().toString(), t.effectiveDefectQty().toPlainString())).toList();
    }

    @GetMapping("/executions/{id}/nodes")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public List<Node> nodes(@PathVariable Long id) {
        return service.nodes(id);
    }

    @GetMapping("/warehouses")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public List<Warehouse> warehouses(@RequestParam(required = false) String keyword) {
        return service.warehouses(keyword);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:create")
    public Acknowledgement create(@Valid @RequestBody Command c) {
        return result(service.create(c));
    }

    @PostMapping("/{id}/revisions")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:revise")
    public Acknowledgement revise(@PathVariable String id, @Valid @RequestBody Command c) {
        return result(service.revise(id, c));
    }

    @PostMapping("/{id}/void")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:void")
    public Acknowledgement voidDeclaration(@PathVariable String id, @Valid @RequestBody Command c) {
        return result(service.voidDeclaration(id, c));
    }
}
