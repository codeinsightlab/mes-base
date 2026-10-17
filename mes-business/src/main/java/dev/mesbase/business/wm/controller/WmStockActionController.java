package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.controller.vo.WmStockActionPageVo;
import dev.mesbase.business.wm.controller.vo.WmStockActionVo;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionPage;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.service.IWmStockActionService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    static WmStockActionVo toView(WmStockActionEntity row) {
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
                row.getReason(),
                row.getDocumentLineId() == null ? null : row.getDocumentLineId().toString(),
                row.getOperationId()
        );
    }
}

