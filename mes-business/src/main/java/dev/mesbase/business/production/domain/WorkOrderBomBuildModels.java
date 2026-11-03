package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkOrderBomBuildModels {
    private WorkOrderBomBuildModels() { }

    public record PreviewRequest(@NotNull Long productId, @NotNull Long bomId, @NotNull BigDecimal plannedQty) { }

    public record Material(String lineId, String materialId, String code, String name, String unitId,
                           BigDecimal quantity, BigDecimal componentQty, BigDecimal baseQty) { }

    public record Route(String id, String code, String name, int version) { }

    public record Problem(String code, String message) { }

    public record OrderPreview(String path, String parentPath, String sourceLineId, String productId,
                               String productCode, String productName, BigDecimal plannedQty, String bomId,
                               String bomCode, String bomVersion, Long bomRevision, String defaultRuleCode,
                               String routingId, List<Route> routings, List<Material> materials,
                               List<Problem> problems) { }

    public record Preview(String fingerprint, boolean complete, List<OrderPreview> orders,
                          List<Problem> problems) { }

    public record OrderChoice(@NotBlank String path, @Size(max = 64) String ruleCode, Long routingId,
                              LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                              @Size(max = 500) String remark) { }

    public record Confirm(@NotBlank @Size(max = 64) String requestId, @NotBlank String fingerprint,
                          @NotNull @Size(min = 1) List<@NotNull @Valid OrderChoice> orders) { }

    public record Confirmation(@NotNull @Valid PreviewRequest source, @NotNull @Valid Confirm confirmation) { }

    public record Result(String buildId, List<WorkOrderModels.Detail> orders) { }
}
