package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.controller.vo.WmStockActionPageVo;
import dev.mesbase.business.wm.controller.vo.WmStockActionVo;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.service.IWmStockActionService;
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
        return BeanConversions.convertPage(service.selectPage(query), WmStockActionPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WmStockActionPermissions.QUERY)
    public WmStockActionVo detail(@PathVariable String id) {
        return toView(service.selectById(id));
    }

    static WmStockActionVo toView(WmStockActionEntity row) {
        return BeanConversions.convert(row, WmStockActionVo.class);
    }
}

