package dev.mesbase.business.wm.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentType;
import java.util.Arrays;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MaterialIssueSubmissionStatus;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wm/stock-documents")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class StockDocumentController {
    private static final String PERMISSION = "business:wm:stock:document:";
    private final StockDocumentService service;
    private final AutoCodeNumbering autoCodes;
    private final MaterialIssueVoidService issueVoid;
    private final AuthorizationService authorization;

    public StockDocumentController(StockDocumentService service, AutoCodeNumbering autoCodes,
                                   MaterialIssueVoidService issueVoid, AuthorizationService authorization) {
        this.service = service;
        this.autoCodes = autoCodes;
        this.issueVoid = issueVoid;
        this.authorization = authorization;
    }

    @GetMapping
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Page page(@ModelAttribute StockDocumentModels.Query query) {
        if (!hasPermission(PERMISSION + "list")) {
            if (query.documentType() != null && !StockDocumentType.MATERIAL_ISSUE.getValue().equals(query.documentType()))
                throw new FoundationException(ErrorCode.FORBIDDEN);
            requirePermission("business:mes:material:issue:list");
            query = new StockDocumentModels.Query(StockDocumentType.MATERIAL_ISSUE.getValue(), query.status(),
                    query.documentNo(), query.offset(), query.limit());
        }
        var page = service.page(query);
        return BeanConversions.convertPage(page, Page.class);
    }

    public record Type(String value, String label, String origin, String requiredDocuments, boolean approvalAvailable,
                       boolean qualityReceipt) {
    }

    @GetMapping("/types")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public List<Type> types() {
        var types = Arrays.stream(StockDocumentType.values());
        if (!hasPermission(PERMISSION + "query")) {
            if (!hasPermission("business:mes:material:issue:list")
                    && !hasPermission("business:mes:material:issue:query"))
                throw new FoundationException(ErrorCode.FORBIDDEN);
            types = types.filter(type -> type == StockDocumentType.MATERIAL_ISSUE);
        }
        return types.map(type -> new Type(type.getValue(), type.getDescription(),
                        type.getOrigin(), type.getRequiredDocuments(), type.isApprovalAvailable(), type.isQualityReceipt()))
                .toList();
    }

    public record NumberingRule(String ruleCode, String ruleName, String prefix) { }

    @GetMapping("/numbering-rules")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "create")
    public List<NumberingRule> numberingRules(@RequestParam String type) {
        if (!AutoCodeRuleType.supports(type)) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return autoCodes.rules(type).stream()
                .map(rule -> new NumberingRule(rule.ruleCode(), rule.ruleName(), rule.prefix())).toList();
    }

    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Detail detail(@PathVariable Long id,
                         @RequestParam(required = false) String documentType) {
        boolean stockQuery = hasPermission(PERMISSION + "query");
        if (!stockQuery) {
            if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(documentType))
                throw new FoundationException(ErrorCode.FORBIDDEN);
            requirePermission("business:mes:material:issue:query");
        }
        var detail = service.detail(id);
        if (!stockQuery && !StockDocumentType.MATERIAL_ISSUE.getValue().equals(detail.document().getDocumentType()))
            throw new FoundationException(ErrorCode.NOT_FOUND);
        if (!stockQuery && !hasPermission("business:mes:material:issue:item:list"))
            detail = new StockDocumentModels.Detail(detail.document(), List.of(), detail.qualitySource(),
                    detail.materialIssueBasis(), detail.materialIssueBasisError());
        return view(detail);
    }

    @GetMapping("/{id}/lines")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public List<Line> lines(@PathVariable Long id,
                            @RequestParam(required = false) String documentType) {
        boolean stockQuery = hasPermission(PERMISSION + "query");
        if (!stockQuery) {
            if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(documentType))
                throw new FoundationException(ErrorCode.FORBIDDEN);
            requirePermission("business:mes:material:issue:item:list");
        }
        var detail = service.detail(id);
        if (!stockQuery && !StockDocumentType.MATERIAL_ISSUE.getValue().equals(detail.document().getDocumentType()))
            throw new FoundationException(ErrorCode.NOT_FOUND);
        return detail.lines().stream().map(StockDocumentController::view).toList();
    }

    @GetMapping("/{id}/lines/{lineId}")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Line line(@PathVariable Long id, @PathVariable Long lineId,
                     @RequestParam(required = false) String documentType) {
        boolean stockQuery = hasPermission(PERMISSION + "query");
        if (!stockQuery) {
            if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(documentType))
                throw new FoundationException(ErrorCode.FORBIDDEN);
            requirePermission("business:mes:material:issue:item:query");
        }
        var detail = service.detail(id);
        if (!stockQuery && !StockDocumentType.MATERIAL_ISSUE.getValue().equals(detail.document().getDocumentType()))
            throw new FoundationException(ErrorCode.NOT_FOUND);
        var row = detail.lines().stream().filter(line -> lineId.equals(line.getId())).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.NOT_FOUND));
        return view(row);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "create")
    public Detail create(@Valid @RequestBody StockDocumentModels.Create command) {
        if (command.ruleCode() == null || command.ruleCode().isBlank())
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        return view(service.create(command));
    }

    @PostMapping("/aggregate")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "create")
    public Detail createAggregate(@Valid @RequestBody StockDocumentModels.AggregateSave command) {
        return view(service.createAggregate(command));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public Detail edit(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Edit command) {
        return view(service.edit(id, command));
    }

    @PutMapping("/{id}/aggregate")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public Detail updateAggregate(@PathVariable Long id,
                                  @Valid @RequestBody StockDocumentModels.AggregateSave command) {
        return view(service.updateAggregate(id, command));
    }

    @PostMapping("/{id}/actions/cancel")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Detail cancel(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Version command) {
        if (!hasPermission(PERMISSION + "cancel")) {
            requirePermission("business:mes:material:issue:update");
            if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(service.detail(id).document().getDocumentType()))
                throw new FoundationException(ErrorCode.FORBIDDEN);
        }
        return view(service.cancel(id, command));
    }

    @PostMapping("/{id}/actions/approve")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Detail approve(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Post command) {
        if (!hasPermission(PERMISSION + "post")) {
            requirePermission("business:mes:material:issue:update");
            if (!StockDocumentType.MATERIAL_ISSUE.getValue().equals(service.detail(id).document().getDocumentType()))
                throw new FoundationException(ErrorCode.FORBIDDEN);
        }
        return view(service.approve(id, command));
    }

    @PostMapping("/{id}/actions/void")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:void")
    public MaterialIssueVoidResult voidMaterialIssue(@PathVariable Long id) {
        return materialIssueVoidResult(id, issueVoid.cancel(service.materialIssueIdentity(id)));
    }

    @PostMapping("/{id}/actions/reconcile-void")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:void")
    public MaterialIssueVoidResult reconcileMaterialIssueVoid(@PathVariable Long id) {
        return materialIssueVoidResult(id, issueVoid.reconcile(service.materialIssueIdentity(id)));
    }

    private MaterialIssueVoidResult materialIssueVoidResult(Long documentId,
                                                             MesMaterialIssueEntity issue) {
        var document = service.detail(documentId).document();
        return new MaterialIssueVoidResult(documentId.toString(), document.getStatus(), issue.getStatus(),
                issue.getVoidOperationId(), issue.getVoidResultStatus(), issue.getVoidRequestedAt(),
                issue.getVoidFinishedAt(), voidMessage(issue));
    }

    private String voidMessage(MesMaterialIssueEntity issue) {
        if (MaterialIssueSubmissionStatus.SUCCEEDED.getValue().equals(issue.getVoidResultStatus()))
            return "本地撤销与库存反向事务已完成";
        if (MaterialIssueSubmissionStatus.UNKNOWN.getValue().equals(issue.getVoidResultStatus()))
            return "本地撤销结果待恢复";
        return null;
    }

    public record MaterialIssueVoidResult(String documentId, String documentStatus, String productionInputStatus,
                                          String voidOperationId, String voidResultStatus,
                                          LocalDateTime voidRequestedAt, LocalDateTime voidFinishedAt,
                                          String message) {
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
        return BeanConversions.convert(b, ProductionBasis.class);
    }

    public record ProductionBasis(String executionId, String productId, String effective,
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
        return BeanConversions.convert(detail, Detail.class);
    }

    private boolean hasPermission(String value) {
        return authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                FactoryContext.required(), new Permission(value));
    }

    private void requirePermission(String value) {
        if (!hasPermission(value)) throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    private static Header view(WmStockDocumentEntity row) {
        return BeanConversions.convert(row, Header.class);
    }

    private static Line view(WmStockDocumentLineEntity row) {
        return BeanConversions.convert(row, Line.class);
    }

    private static String string(Long id) {
        return Objects.toString(id, null);
    }

    public record Header(String id, String documentNo, String documentType, String status, String reason,
                         String remark, String sourceWorkorderId, String sourceExecutionId,
                         String sourceMaterialIssueId, String createBy,
                         LocalDateTime createTime,
                         String updateBy, LocalDateTime updateTime, String postedBy, LocalDateTime postedAt,
                         String cancelledBy, LocalDateTime cancelledAt, String revision, String postRequestId) {
    }

    public record Line(String id, String documentId, int lineNo, String materialId, String warehouseId,
                       String stockId, String batchNo, String binCode, String unitId, String quantity,
                       String remark, String stockActionId, String revision) {
    }

    public record QualitySource(String sourceDeclarationId, String sourceTreatmentId, String sourceQualityExecutionId) {
    }

    public record MaterialIssueBasis(String requestId, String code, String workorderId, String outputQty,
                                     String routingId, List<String> routingNodeIds) {
    }

    public record Detail(Header document, List<Line> lines, QualitySource qualitySource,
                         MaterialIssueBasis materialIssueBasis, String materialIssueBasisError) {
    }

    public record Page(List<Header> items, long total, int offset, int limit) {
    }

    public record Basis(String workorderId, String productId, String status, String handedOver,
                        String accepted, String manuallyReceived, String remaining) {
    }
}
