package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.IMesOperatorQualificationService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/operator-qualifications")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesOperatorQualificationController {
    private final IMesOperatorQualificationService service;

    public MesOperatorQualificationController(IMesOperatorQualificationService service) {
        this.service = service;
    }

    public record Change(@NotNull @Min(0) Integer revision) {
    }

    public record Operation(String operationId, String code, String name, String status) {
    }

    public record Summary(String userId, boolean allOperations, int revision, boolean validUser,
                          long explicitTotal, long enabledExplicitTotal, List<Operation> operations, int offset,
                          int limit) {
    }

    @GetMapping("/{userId}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesOperationUserPermissions.LIST)
    public Summary summary(@PathVariable Long userId, @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(defaultValue = "20") int limit) {
        return BeanConversions.convert(service.summary(userId, offset, limit), Summary.class);
    }

    @PostMapping("/{userId}/all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesOperationUserPermissions.CREATE)
    public void grant(@PathVariable Long userId, @Valid @RequestBody Change change) {
        service.grantAll(userId, change.revision());
    }

    @DeleteMapping("/{userId}/all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesOperationUserPermissions.DELETE)
    public void revoke(@PathVariable Long userId, @Valid @RequestBody Change change) {
        service.revokeAll(userId, change.revision());
    }
}
