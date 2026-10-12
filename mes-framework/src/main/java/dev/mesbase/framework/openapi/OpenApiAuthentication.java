package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiPrincipal;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 不授予任何后台authority；只保存已验证外部身份，不保留原始凭证。
 */
public final class OpenApiAuthentication extends AbstractAuthenticationToken {
    private final OpenApiPrincipal principal;

    public OpenApiAuthentication(OpenApiPrincipal principal) {
        super(Collections.emptyList());
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public OpenApiPrincipal getPrincipal() {
        return principal;
    }
}
