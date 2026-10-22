package dev.mesbase.business.wm.controller;

import java.util.Objects;

import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wm/stock-documents")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class StockDocumentController {
    private static final String PERMISSION = "business:wm:stock:document:";
    private final StockDocumentService service;

    public StockDocumentController(StockDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "list")
    public Page page(@ModelAttribute StockDocumentModels.Query query) {
        var page = service.page(query);
        return new Page(page.items().stream().map(StockDocumentController::view)
                .toList(), page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "query")
    public Detail detail(@PathVariable Long id) {
        return view(service.detail(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "create")
    public Detail create(@Valid @RequestBody StockDocumentModels.Create command) {
        return view(service.create(command));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public Detail edit(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Edit command) {
        return view(service.edit(id, command));
    }

    @PostMapping("/{id}/actions/cancel")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "cancel")
    public Detail cancel(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Version command) {
        return view(service.cancel(id, command));
    }

    @PostMapping("/{id}/actions/post")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "post")
    public Detail post(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Post command) {
        return view(service.post(id, command));
    }

    @PostMapping("/{id}/lines")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public Line addLine(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Line command) {
        return view(service.putLine(id, null, command));
    }

    @PutMapping("/{id}/lines/{lineId}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public Line editLine(@PathVariable Long id, @PathVariable Long lineId,
                         @Valid @RequestBody StockDocumentModels.Line command) {
        return view(service.putLine(id, lineId, command));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public void removeLine(@PathVariable Long id, @PathVariable Long lineId,
                           @Valid @RequestBody StockDocumentModels.Version command) {
        service.removeLine(id, lineId, command);
    }

    @GetMapping("/executions/{id}/basis")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "query")
    public ProductionBasis productionBasis(@PathVariable Long id) {
        var b = service.productionBasis(id);
        return new ProductionBasis(b.executionId().toString(), b.productId().toString(), b.effective()
                .toPlainString(), b.released().toPlainString(), b.received().toPlainString(), b.remaining()
                .toPlainString(), b.canReceive(), b.blockedReason());
    }

    public record ProductionBasis(String executionId, String productId, String effective, String released,
                                  String received, String remaining, boolean canReceive, String blockedReason) {
    }

    @GetMapping("/cancelled-workorders/{id}/basis")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "query")
    public Basis basis(@PathVariable Long id) {
        var b = service.cancellationBasis(id);
        return new Basis(b.workorderId().toString(), b.productId().toString(), b.status(),
                b.handedOver().toPlainString(), b.accepted().toPlainString(), b.manuallyReceived().toPlainString(),
                b.remaining().toPlainString());
    }

    private static Detail view(StockDocumentModels.Detail detail) {
        return new Detail(view(detail.document()), detail.lines().stream().map(StockDocumentController::view).toList());
    }

    private static Header view(WmStockDocumentEntity row) {
        return new Header(row.getId().toString(), row.getDocumentNo(), row.getDocumentType(), row.getStatus(),
                row.getReason(), row.getRemark(), string(row.getSourceWorkorderId()), string(row.getSourceExecutionId()), row.getCreateBy(), row.getCreateTime(),
                row.getUpdateBy(), row.getUpdateTime(), string(row.getPostedBy()), row.getPostedAt(),
                string(row.getCancelledBy()), row.getCancelledAt(), row.getRevision()
                .toString(), row.getPostRequestId());
    }

    private static Line view(WmStockDocumentLineEntity row) {
        return new Line(row.getId().toString(), row.getDocumentId().toString(), row.getLineNo(),
                row.getMaterialId().toString(), row.getWarehouseId().toString(), row.getStockId().toString(),
                row.getBatchNo(), row.getBinCode(), row.getUnitId().toString(), row.getQuantity().toPlainString(),
                row.getRemark(), row.getStockActionId(), row.getRevision().toString());
    }

    private static String string(Long id) {
        return Objects.toString(id, null);
    }

    public record Header(String id, String documentNo, String documentType, String status, String reason,
                         String remark, String sourceWorkorderId, String sourceExecutionId, String createBy,
                         LocalDateTime createTime,
                         String updateBy, LocalDateTime updateTime, String postedBy, LocalDateTime postedAt,
                         String cancelledBy, LocalDateTime cancelledAt, String revision, String postRequestId) {
    }

    public record Line(String id, String documentId, int lineNo, String materialId, String warehouseId,
                       String stockId, String batchNo, String binCode, String unitId, String quantity,
                       String remark, String stockActionId, String revision) {
    }

    public record Detail(Header document, List<Line> lines) {
    }

    public record Page(List<Header> items, long total, int offset, int limit) {
    }

    public record Basis(String workorderId, String productId, String status, String handedOver,
                        String accepted, String manuallyReceived, String remaining) {
    }
}
