package dev.mesbase.business.sales.controller;

import dev.mesbase.business.sales.controller.vo.MesSalesOrderItemPageVo;
import dev.mesbase.business.sales.controller.vo.MesSalesOrderItemVo;
import dev.mesbase.business.sales.domain.MesSalesOrderItemEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemQueryDto;
import dev.mesbase.business.sales.service.IMesSalesOrderItemService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售订单明细 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/sales/mes-sales-order-item")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesSalesOrderItemController {
    private final IMesSalesOrderItemService service;

    public MesSalesOrderItemController(IMesSalesOrderItemService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderItemPermissions.LIST)
    public MesSalesOrderItemPageVo list(@Valid @ModelAttribute MesSalesOrderItemQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesSalesOrderItemPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderItemPermissions.QUERY)
    public MesSalesOrderItemVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    private static MesSalesOrderItemVo toView(MesSalesOrderItemEntity row) {
        return BeanConversions.convert(row, MesSalesOrderItemVo.class);
    }
}

