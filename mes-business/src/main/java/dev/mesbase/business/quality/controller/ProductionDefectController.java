package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.domain.QualityTreatmentModels;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.ListContext;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Names;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Node;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Processing;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Warehouse;
import dev.mesbase.business.quality.service.ProductionDefectService.Result;
import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality/defects")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionDefectController {
    private final ProductionDefectService service;

    public ProductionDefectController(ProductionDefectService service) {
        this.service = service;
    }

    public record Version(String id, String declarationId, Integer revisionNo, String recordAction,
                          String revisionReason, String executionId, String affectedNodeId,
                          String responsibleUserId, String reportedBy, String recordedBy,
                          String wasteCategory,
                          String defectReason, String responsibilityRemark, String defectQty, LocalDateTime occurredAt,
                          LocalDateTime recordedAt, String affectedNodeName,
                          String responsibleUserName, String reportedByName, String recordedByName,
                          String defectTypeId, String defectTypeCodeSnapshot, String defectTypeNameSnapshot,
                          Integer defectTypeRevision, String processingState, String pendingQuantity,
                          String returnedQuantity, String transferredQuantity, String workorderCode, String productCode,
                          String productName,
                          String workorderStatus, String executionStatus, String source, String batchNo,
                          String specification, String receiptState) {
    }

    public record Page(List<Version> items, long total, int offset, int limit) {
    }

    public record Acknowledgement(Version record, boolean success, boolean externalExecuted, String executionMode,
                                  String externalReference) {
    }

    public record Total(String affectedNodeId, String effectiveDefectQty) {
    }

    public static Version view(MesProductionDefectEntity r) {
        return view(r, new Names(Map.of(), Map.of()));
    }

    public static Version view(MesProductionDefectEntity r, Names names) {
        return view(r, names, null, null);
    }

    private record Labels(String affectedNodeName,
                          String responsibleUserName,
                          String reportedByName, String recordedByName, String processingState, String pendingQuantity,
                          String returnedQuantity, String transferredQuantity, String receiptState,
                          @JsonUnwrapped ListContext context) {
    }

    private static Version view(MesProductionDefectEntity r, Names names, Processing treatment, ListContext context) {
        String state = null, pending = null, returned = null, transferred = null;
        if (treatment != null) {
            state = treatment.state().getValue();
            if (treatment.state() != State.UNRESOLVED) {
                pending = treatment.pendingDispositionQty().toPlainString();
                returned = treatment.returnedQty().toPlainString();
                transferred = treatment.transferredQty().toPlainString();
            }
        }
        String receipt = null;
        if (treatment != null) {
            var status = QualityTreatmentModels.receiptState(treatment.state(), r.getDefectQty(), treatment.transferredQty());
            if (status != null) receipt = status.getValue();
        }
        return BeanConversions.convertWith(r, new Labels(
                label(names.nodes(), r.getAffectedNodeId()),
                label(names.users(), r.getResponsibleUserId()),
                label(names.users(), r.getReportedBy()), label(names.users(), r.getRecordedBy()),
                state, pending, returned, transferred, receipt, context), Version.class);
    }

    private static String label(Map<Long, String> names, Long id) {
        if (id == null) return null;
        return names.get(id);
    }

    private static String id(Long id) {
        return Objects.toString(id, null);
    }

    public static Acknowledgement result(Result r) {
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
        return BeanConversions.convertPage(p, r -> view(r, p.names(), p.treatments().get(r.getDeclarationId()),
                p.contexts().get(r.getDeclarationId())), Page::new);
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
        return BeanConversions.convertPage(p, r -> view(r, p.names()), Page::new);
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
