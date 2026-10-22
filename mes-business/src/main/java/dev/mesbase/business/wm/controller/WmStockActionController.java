package dev.mesbase.business.wm.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.wm.controller.vo.WmStockActionPageVo;
import dev.mesbase.business.wm.controller.vo.WmStockActionVo;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
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
        Page<WmStockActionEntity> page = service.selectPage(query);
        return new WmStockActionPageVo(
                page.stream().map(WmStockActionController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
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
                Objects.toString(row.getStockId(), null),
                Objects.toString(row.getMaterialId(), null),
                Objects.toString(row.getUnitId(), null),
                Objects.toString(row.getWarehouseId(), null),
                row.getBatchNo(),
                row.getBinCode(),
                row.getActionType(),
                Objects.toString(row.getQuantityDelta(), null),
                Objects.toString(row.getAvailableDelta(), null),
                Objects.toString(row.getQuantityBefore(), null),
                Objects.toString(row.getQuantityAfter(), null),
                Objects.toString(row.getAvailableBefore(), null),
                Objects.toString(row.getAvailableAfter(), null),
                row.getSourceType(),
                row.getSourceId(),
                row.getReferenceActionId(),
                Objects.toString(row.getExecutionId(), null),
                Objects.toString(row.getOperatorUserId(), null),
                row.getOccurredAt(),
                row.getRecordedAt(),
                row.getReason(),
                Objects.toString(row.getDocumentLineId(), null),
                row.getOperationId()
        );
    }
}

