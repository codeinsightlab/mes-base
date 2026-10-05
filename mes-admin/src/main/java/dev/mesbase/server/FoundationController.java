package dev.mesbase.server;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.platform.web.EndpointAccess;
import static dev.mesbase.platform.web.EndpointAccess.Kind.*;
@RestController
public final class FoundationController {
    public record HealthView(String status) {}
    public record PreferenceRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String factoryId) {}
    private final FoundationService service;
    public FoundationController(FoundationService service) { this.service=service; }
    @GetMapping("/health") @EndpointAccess(PUBLIC)
    public HealthView health() { return new HealthView("UP"); }
    @GetMapping("/api/foundation/factories") @EndpointAccess(AUTHENTICATED)
    public List<FoundationService.FactoryView> factories(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit) { return service.options(offset,limit); }
    @GetMapping("/api/foundation/context") @EndpointAccess(AUTHENTICATED)
    public FoundationService.ScopeView context() { return service.context(); }
    @PutMapping("/api/foundation/preference") @EndpointAccess(AUTHENTICATED)
    public FoundationService.ScopeView preference(Authentication actor,@Valid @RequestBody PreferenceRequest request) { return service.remember(new AuthenticatedActor(actor.getName()),request.factoryId()); }
    @GetMapping("/api/foundation/access") @EndpointAccess(value=AUTHORIZED,permission="foundation:access:read")
    public FoundationService.AccessView access() { return service.access(); }
    @GetMapping("/api/foundation/erp/connection") @EndpointAccess(value=AUTHORIZED,permission="foundation:erp:inspect")
    public void erp() { service.inspectErp(); }
}
