package dev.mesbase.server.integration.e10;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import dev.mesbase.web.controller.DigiwinE10PreviewController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: 真实 HTTP Controller 与授权拦截器；候选服务为 Mock，无真实 ERP 调用。
 */
class DigiwinE10PreviewHttpTest {
    @Test
    void allFourEndpointsRequireTheirOwnFactoryPermissionAndRejectOtherFactory() throws Exception {
        for (var kind : Kind.values()) {
            for (boolean command : List.of(false, true)) {
                var service = mock(DigiwinE10SyncPreview.class);
                var selection = mock(FactorySelectionService.class);
                var authorization = mock(AuthorizationService.class);
                var actor = new AuthenticatedActor("TEST_ONLY");
                var scope = new FactoryScope(new FactoryId("A"));
                when(selection.resolve(any(), any())).thenAnswer(invocation -> {
                    Optional<FactoryId> selected = invocation.getArgument(1);
                    return selected.map(FactoryScope::new);
                });
                var mvc = MockMvcBuilders.standaloneSetup(new DigiwinE10PreviewController(service))
                        .addInterceptors(new FoundationAccessInterceptor(selection, authorization))
                        .setControllerAdvice(new FoundationErrorAdvice()).build();
                String path = kind == Kind.WORKORDER ? "/api/production/workorder/erp-preview"
                        : "/api/wm/wm-warehouse/erp-preview";
                String permission = kind == Kind.WORKORDER ? "workorder:sync" : "business:wm:warehouse:sync";
                try {
                    mvc.perform(request(path, command)).andExpect(status().isUnauthorized());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken("TEST_ONLY", "TEST_ONLY", List.of()));
                    // 普通 CRUD 或仅有工厂请求头不授予同步准备权限。
                    when(authorization.hasPermission(actor, scope, new Permission("workorder:list"))).thenReturn(true);
                    mvc.perform(request(path, command).header("X-Factory-Id", "A")).andExpect(status().isForbidden());
                    when(authorization.hasPermission(actor, scope, new Permission(permission))).thenReturn(true);
                    mvc.perform(request(path, command).header("X-Factory-Id", "B")).andExpect(status().isForbidden());
                    verifyNoInteractions(service);
                    if (command) {
                        when(service.preview(kind, 1, 20)).thenReturn(new DigiwinE10SyncPreview.Preview(
                                "PREVIEW_ONLY", "TEST_RUN", "DIGIWIN_E10", "TEST_BINDING", "1", 1, 20,
                                0, 0, 0, 0, false, List.of(), List.of("TEST_ONLY_BLOCKER")));
                        mvc.perform(request(path, true).header("X-Factory-Id", "A"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREVIEW_ONLY"))
                                .andExpect(jsonPath("$.created").value(0));
                        verify(service).preview(kind, 1, 20);
                    } else {
                        when(service.capability(kind)).thenReturn(new DigiwinE10SyncPreview.Capability(true, false,
                                List.of("TEST_ONLY_BLOCKER")));
                        mvc.perform(request(path, false).header("X-Factory-Id", "A"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.writeEnabled").value(false));
                        verify(service).capability(kind);
                    }
                } finally {
                    SecurityContextHolder.clearContext();
                    FactoryContext.clear();
                }
            }
        }
    }

    private static MockHttpServletRequestBuilder request(String path, boolean command) {
        return command ? post(path) : get(path);
    }
}
