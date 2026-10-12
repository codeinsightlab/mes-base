package dev.mesbase.web.controller.personal;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.preference.TablePreferenceService;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.preference.TableColumnPreference;
import dev.mesbase.system.preference.TablePreferenceKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/factory/table-preferences")
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class TablePreferenceController {
    public record ColumnInput(@NotBlank @Size(max = 64) String key, @NotNull JsonNode visible) {
    }

    public record SaveInput(@NotBlank @Size(max = 512) String url, @NotBlank @Size(max = 192) String tableKey,
                            @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull ColumnInput> columns) {
    }

    public record Result(List<TableColumnPreference> columns) {
    }

    private final TablePreferenceService service;

    public TablePreferenceController(TablePreferenceService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Result get(@RequestParam String url, @RequestParam String tableKey) {
        return new Result(service.get(new TablePreferenceKey(url, tableKey)));
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public void save(@Valid @RequestBody SaveInput input) {
        List<TableColumnPreference> columns = input.columns().stream().map(column -> {
            if (!column.visible().isBoolean()) throw new FoundationException(ErrorCode.BAD_REQUEST);
            return new TableColumnPreference(column.key(), column.visible().asBoolean());
        }).toList();
        service.save(new TablePreferenceKey(input.url(), input.tableKey()), columns);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public void reset(@RequestParam String url, @RequestParam String tableKey) {
        service.reset(new TablePreferenceKey(url, tableKey));
    }
}
