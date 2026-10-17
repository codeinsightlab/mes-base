package dev.mesbase.server.integration.e10;

import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.web.controller.system.ManagedFactoryController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 Controller/权限拦截器，服务为 Mock；不读取用户连接。
 */
class FactoryOapiHttpTest {
    @Test
    void bothEndpointsRequirePlatformRoleAndFactoryEditPermissionWithoutUsingFactoryHeaderAsAuthorization() throws Exception {
        var platform = mock(PlatformAuthorization.class);
        var oapi = mock(FactoryOapiService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ManagedFactoryController(mock(ManagedDatasourceService.class), oapi))
                .addInterceptors(new FoundationAccessInterceptor(mock(FactorySelectionService.class), mock(AuthorizationService.class), platform))
                .setControllerAdvice(new FoundationErrorAdvice()).build();
        try {
            mvc.perform(get("/api/system/factory/A/oapi")).andExpect(status().isUnauthorized());
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken("TEST", "TEST", List.of()));
            mvc.perform(get("/api/system/factory/A/oapi").header("X-Factory-Id", "A"))
                    .andExpect(status().isForbidden());
            var actor = new AuthenticatedActor("TEST");
            when(platform.hasPlatformRole(actor)).thenReturn(true);
            mvc.perform(get("/api/system/factory/A/oapi")).andExpect(status().isForbidden());
            mvc.perform(put("/api/system/factory/A/oapi").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(oapi);
            when(platform.hasPermission(actor, new Permission("system:factory:edit"))).thenReturn(true);
            when(oapi.get("A")).thenReturn(new FactoryOapiService.View("A", 1, null, 0, false, null));
            mvc.perform(get("/api/system/factory/A/oapi").header("X-Factory-Id", "B")).andExpect(status().isOk());
            mvc.perform(put("/api/system/factory/A/oapi").contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
