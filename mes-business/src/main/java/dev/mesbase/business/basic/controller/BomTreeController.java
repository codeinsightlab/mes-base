package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.service.BomTreePlans;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.*;
import dev.mesbase.core.security.*;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/basic/material-bom/tree")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class BomTreeController {
    private final BomTreePlans plans;
    private final AuthorizationService auth;

    public BomTreeController(BomTreePlans plans, AuthorizationService auth) {
        this.plans = plans;
        this.auth = auth;
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.QUERY)
    public BomTreePlans.Open open(@PathVariable long id) {
        return plans.open(id);
    }

    private void access() {
        for (var p : java.util.List.of(MaterialBomPermissions.QUERY, MaterialBomPermissions.CREATE))
            if (!auth.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication()
                    .getName()), FactoryContext.required(), new Permission(p)))
                throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @PostMapping("/preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.UPDATE)
    public BomTreePlans.Preview preview(@RequestBody BomTreePlans.Plan p) {
        access();
        return plans.preview(p);
    }

    @PostMapping("/confirm")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.UPDATE)
    public java.util.Map<String, String> confirm(@RequestBody BomTreePlans.Confirm c) {
        access();
        return java.util.Map.of("root", plans.confirm(c));
    }
}
