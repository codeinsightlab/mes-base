// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.controller.vo.WmStockActionPageVo;
import dev.mesbase.business.wm.controller.vo.WmStockActionVo;
import dev.mesbase.business.wm.domain.WmStockActionCreateCommand;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionPage;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.domain.WmStockActionUpdateCommand;
import dev.mesbase.business.wm.service.IWmStockActionService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地仓储不可变收发存动作事实 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/wm/wm-stock-action")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WmStockActionController {
    private final IWmStockActionService service;

    public WmStockActionController(IWmStockActionService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmStockActionPermissions.LIST)
    public WmStockActionPageVo list(@Valid @ModelAttribute WmStockActionQueryDto query) {
        WmStockActionPage page = service.selectPage(query);
        return new WmStockActionPageVo(
                page.items().stream().map(WmStockActionController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmStockActionPermissions.QUERY)
    public WmStockActionVo detail(@PathVariable String id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmStockActionPermissions.CREATE)
    public WmStockActionVo create(@Valid @RequestBody WmStockActionCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmStockActionPermissions.UPDATE)
    public WmStockActionVo update(@PathVariable String id,
            @Valid @RequestBody WmStockActionUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmStockActionPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull String> ids) {
        service.deleteByIds(ids);
    }


    private static WmStockActionVo toView(WmStockActionEntity row) {
        return new WmStockActionVo(
                row.getId(),
                row.getRequestHash(),
                row.getStockId() == null ? null : row.getStockId().toString(),
                row.getMaterialId() == null ? null : row.getMaterialId().toString(),
                row.getUnitId() == null ? null : row.getUnitId().toString(),
                row.getWarehouseId() == null ? null : row.getWarehouseId().toString(),
                row.getBatchNo(),
                row.getBinCode(),
                row.getActionType(),
                row.getQuantityDelta() == null ? null : row.getQuantityDelta().toString(),
                row.getAvailableDelta() == null ? null : row.getAvailableDelta().toString(),
                row.getQuantityBefore() == null ? null : row.getQuantityBefore().toString(),
                row.getQuantityAfter() == null ? null : row.getQuantityAfter().toString(),
                row.getAvailableBefore() == null ? null : row.getAvailableBefore().toString(),
                row.getAvailableAfter() == null ? null : row.getAvailableAfter().toString(),
                row.getSourceType(),
                row.getSourceId(),
                row.getReferenceActionId(),
                row.getExecutionId() == null ? null : row.getExecutionId().toString(),
                row.getOperatorUserId() == null ? null : row.getOperatorUserId().toString(),
                row.getOccurredAt(),
                row.getRecordedAt(),
                row.getReason()
        );
    }
}

