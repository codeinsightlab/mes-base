package dev.mesbase.web.controller.system;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.domain.model.RegisterBody;
import dev.mesbase.framework.web.service.SysRegisterService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** SOURCE_PRODUCT_PORT of Source public registration; service owns the enable switch and transaction. */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysRegisterController {
    private final SysRegisterService registration;
    public SysRegisterController(SysRegisterService registration) { this.registration = registration; }

    @PostMapping("/api/register")
    @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    public AjaxResult register(@Valid @RequestBody RegisterBody input, HttpServletRequest request) {
        registration.register(input, request);
        return AjaxResult.success();
    }
}
