package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

/** 平台诊断，不查询MES业务表，不读取内部登录用户。 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiDiagnosticsService {
    private final Clock clock;
    private final OpenApiClientService clients;
    private final OpenApiCatalogService catalog;
    public OpenApiDiagnosticsService(@Qualifier("openApiClock") Clock clock, OpenApiClientService clients, OpenApiCatalogService catalog) {
        this.clock = clock; this.clients = clients; this.catalog = catalog;
    }
    public OpenApiPingVo ping() { return new OpenApiPingVo("pong", clock.instant().toString()); }
    public OpenApiClientIdentityVo client(OpenApiPrincipal principal) {
        requireIdentity(principal);
        return new OpenApiClientIdentityVo(principal.getAppKey());
    }
    public OpenApiCapabilitiesVo capabilities(OpenApiPrincipal principal) {
        requireIdentity(principal);
        List<String> declared = catalog.list().stream().map(OpenApiCatalogVo::getApiCode).collect(Collectors.toList());
        List<String> granted = clients.apiCodes(principal.getClientId());
        return new OpenApiCapabilitiesVo(granted.stream().filter(declared::contains).distinct().sorted().collect(Collectors.toList()));
    }
    private void requireIdentity(OpenApiPrincipal principal) {
        if (principal == null) throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
    }
}
