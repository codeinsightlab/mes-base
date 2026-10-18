package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.domain.ProductionDefectV2Models.Command;
import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.quality.controller.ProductionDefectController.Acknowledgement;
import dev.mesbase.business.quality.controller.ProductionDefectController.Version;
import dev.mesbase.business.quality.controller.OperationDefectTypeController.View;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Node;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/quality/defects")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionDefectV2Controller {
    private final ProductionDefectService service;

    public ProductionDefectV2Controller(ProductionDefectService service) {
        this.service = service;
    }

    public record Context(String executionId, String nodeId, String operationId, String operationName,
                          String productId, String productCode, String productName, List<View> types,
                          long typeTotal, int offset, int limit, boolean canOverrideWasteCategory) {
    }

    @GetMapping("/executions/{executionId}/nodes/{nodeId}/declaration-context")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:create")
    public Context context(@PathVariable Long executionId, @PathVariable Long nodeId,
                           @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(defaultValue = "100") int limit) {
        var c = service.declarationContext(executionId, nodeId, offset, limit);
        return new Context(c.executionId().toString(), c.nodeId().toString(), c.operationId()
                .toString(), c.operationName(),
                c.productId().toString(), c.productCode(), c.productName(), c.types().stream()
                .map(OperationDefectTypeController::view).toList(),
                c.typeTotal(), offset, limit, c.canOverrideWasteCategory());
    }

    @GetMapping("/executions/{executionId}/declaration-nodes")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:create")
    public List<Node> nodes(@PathVariable Long executionId) {
        return service.nodes(executionId);
    }

    @PostMapping("/declarations")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:create")
    public Acknowledgement declare(@RequestBody Command input) {
        return ProductionDefectController.result(service.declare(input));
    }

    @GetMapping("/declarations/requests/{requestId}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:create")
    public Version request(@PathVariable String requestId) {
        return ProductionDefectController.view(service.ownRequest(requestId));
    }
}
