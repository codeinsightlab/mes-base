package dev.mesbase.business.production.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import dev.mesbase.business.production.controller.vo.ProductionIdentityVo;
import dev.mesbase.business.production.domain.ProductionScanInputMethod;
import dev.mesbase.business.production.domain.ProductionScanType;
import dev.mesbase.business.production.service.ProductionScanService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/scan")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionScanController {
    private final ProductionScanService scans;

    public ProductionScanController(ProductionScanService scans) {
        this.scans = scans;
    }

    public record Input(@NotBlank String type, @NotBlank @Size(max = 2048) String code,
                        @NotBlank String inputMethod) {
        @JsonAnySetter
        public void rejectUnexpected(String field, Object value) {
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
    }

    @PostMapping
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public ProductionIdentityVo scan(@Valid @RequestBody Input input, HttpServletRequest request) {
        var type = ProductionScanType.from(input.type());
        var method = ProductionScanInputMethod.from(input.inputMethod());
        if (type == null || method == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return BeanConversions.convert(scans.scan(type, input.code(), method,
                RequestBoundaryFilter.requestId(request)), ProductionIdentityVo.class);
    }
}
