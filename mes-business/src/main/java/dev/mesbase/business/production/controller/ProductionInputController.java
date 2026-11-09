package dev.mesbase.business.production.controller;

import dev.mesbase.business.wm.controller.StockDocumentController;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockCommand;
import dev.mesbase.business.wm.controller.vo.WmStockActionVo;
import dev.mesbase.business.production.controller.vo.RoutingDetailVo;
import dev.mesbase.business.production.domain.ProductionCombinationModels;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/input")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInputController {
    public static final String PERMISSION = "business:mes:material:issue:update";
    private final ProductionInputService service;

    public ProductionInputController(ProductionInputService service) {
        this.service = service;
    }

    public record PreviewView(RoutingDetailVo definition, ExecutionActionController.PathView actual) {
    }

    public record ResultView(String requestId, String documentId, String executionCode, String code,
                             String status, String confirmedInputQty, boolean stockApplied) {
    }

    @GetMapping("/preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public PreviewView preview(@RequestParam String code, @RequestParam Long workorderId) {
        var result = service.preview(code, workorderId);
        ExecutionActionController.PathView actual = null;
        if (result.actual() != null) actual = ExecutionActionController.toView(result.actual());
        return new PreviewView(RoutingController.detailView(result.definition()), actual);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION)
    public ResultView confirm(@RequestBody ProductionCombinationModels.Confirm request) {
        return BeanConversions.convert(service.confirm(request), ResultView.class);
    }

    @PostMapping("/documents/{documentId}/actions/cancel")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:material:issue:void")
    public StockDocumentController.Detail cancelMaterialIssue(@org.springframework.web.bind.annotation.PathVariable Long documentId,
                                                               @RequestBody StockDocumentModels.Cancellation command) {
        return StockDocumentController.view(service.reverseMaterialIssue(documentId, command));
    }

    @PostMapping("/documents/{documentId}/returns")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:return")
    public WmStockActionVo returnMaterialIssue(@org.springframework.web.bind.annotation.PathVariable Long documentId,
                                               @Valid @RequestBody StockCommand command) {
        return BeanConversions.convert(service.returnMaterialIssue(documentId, command), WmStockActionVo.class);
    }
}
