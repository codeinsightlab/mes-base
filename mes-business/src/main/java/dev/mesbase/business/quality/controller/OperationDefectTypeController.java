package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.service.OperationDefectTypeService;
import dev.mesbase.business.quality.service.OperationDefectTypeService.Input;
import dev.mesbase.business.quality.domain.OperationDefectTypeEntity;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/production/operation/{operationId}/defect-types")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperationDefectTypeController {
    private final OperationDefectTypeService service;

    public OperationDefectTypeController(OperationDefectTypeService service) {
        this.service = service;
    }

    public record View(String id, String operationId, String code, String name, String defaultCostCategory,
                       String status, Integer sort, String remark, Integer revision) {
    }

    public record Page(List<View> items, long total, int offset, int limit) {
    }

    public static View view(OperationDefectTypeEntity r) {
        return new View(r.getId().toString(), r.getOperationId().toString(), r.getCode(), r.getName(),
                r.getDefaultCostCategory(), r.getStatus(), r.getSort(), r.getRemark(), r.getRevision());
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:operation:defect:type:read")
    public Page page(@PathVariable Long operationId, @RequestParam(defaultValue = "0") int offset,
                     @RequestParam(defaultValue = "20") int limit) {
        var p = service.page(operationId, false, offset, limit);
        return new Page(p.items().stream().map(OperationDefectTypeController::view).toList(), p.total(), offset, limit);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:operation:defect:type:create")
    public View create(@PathVariable Long operationId, @RequestBody Input input) {
        return view(service.save(operationId, null, input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:operation:defect:type:update")
    public View update(@PathVariable Long operationId, @PathVariable Long id, @RequestBody Input input) {
        return view(service.save(operationId, id, input));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:operation:defect:type:delete")
    public void delete(@PathVariable Long operationId, @PathVariable Long id, @RequestParam int expectedRevision) {
        service.delete(operationId, id, expectedRevision);
    }
}
