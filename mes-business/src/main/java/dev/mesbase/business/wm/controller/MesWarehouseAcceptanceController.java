package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.controller.vo.MesWarehouseAcceptancePageVo;
import dev.mesbase.business.wm.controller.vo.MesWarehouseAcceptanceVo;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptancePage;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceQueryDto;
import dev.mesbase.business.wm.service.IMesWarehouseAcceptanceService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成品仓储接收事实 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/wm/mes-warehouse-acceptance")
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesWarehouseAcceptanceController {
    private final IMesWarehouseAcceptanceService service;

    public MesWarehouseAcceptanceController(IMesWarehouseAcceptanceService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesWarehouseAcceptancePermissions.LIST)
    public MesWarehouseAcceptancePageVo list(@Valid @ModelAttribute MesWarehouseAcceptanceQueryDto query) {
        MesWarehouseAcceptancePage page = service.selectPage(query);
        return new MesWarehouseAcceptancePageVo(
                page.items().stream().map(MesWarehouseAcceptanceController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesWarehouseAcceptancePermissions.QUERY)
    public MesWarehouseAcceptanceVo detail(@PathVariable String id) {
        return toView(service.selectById(id));
    }


    private static MesWarehouseAcceptanceVo toView(MesWarehouseAcceptanceEntity row) {
        return new MesWarehouseAcceptanceVo(
                row.getId(),
                row.getReceiptId(),
                row.getExecutionId() == null ? null : row.getExecutionId().toString(),
                row.getProductId() == null ? null : row.getProductId().toString(),
                row.getStockId() == null ? null : row.getStockId().toString(),
                row.getQuantityDelta() == null ? null : row.getQuantityDelta().toString(),
                row.getReferenceId(),
                row.getStockActionId(),
                row.getRequestHash(),
                row.getOperatorUserId() == null ? null : row.getOperatorUserId().toString(),
                row.getRecordedAt(),
                row.getReason()
        );
    }
}

