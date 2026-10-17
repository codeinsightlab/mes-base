package dev.mesbase.server;

import dev.mesbase.platform.web.FoundationErrorAdvice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpInputErrorContractTest {
    private final MockMvc http = MockMvcBuilders.standaloneSetup(new InputProbe())
            .setControllerAdvice(new FoundationErrorAdvice()).build();

    @RestController
    static class InputProbe {
        record Body(@NotBlank String value) { }
        @GetMapping("/api/production/reports")
        String reports(@RequestParam Long executionId, @RequestParam Long routingNodeId) { return "ok"; }
        @PostMapping("/input")
        String body(@Valid @RequestBody Body body) { return body.value(); }
        @GetMapping("/internal-failure")
        String internal() { throw new IllegalStateException("TEST ONLY private diagnostic"); }
    }

    @Test
    void malformedInputUsesExistingSafe400Protocol() throws Exception {
        http.perform(get("/api/production/reports").param("executionId", "204"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        http.perform(get("/api/production/reports").param("executionId", "invalid").param("routingNodeId", "1024"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        http.perform(post("/input").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        http.perform(post("/input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void internalFailureRemains500WithoutDiagnosticDisclosure() throws Exception {
        http.perform(get("/internal-failure")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
