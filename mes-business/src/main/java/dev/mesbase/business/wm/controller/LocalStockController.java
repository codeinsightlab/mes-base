package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.StockCommand;
import dev.mesbase.business.wm.domain.StockActionType;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.wm.controller.vo.WmStockActionVo;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/wm/local-stock")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LocalStockController {
    private final LocalStockService service;

    public LocalStockController(LocalStockService service) {
        this.service = service;
    }

    @PostMapping("/opening")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:opening")
    public WmStockActionVo opening(@Valid @RequestBody StockCommand input) {
        return WmStockActionController.toView(service.execute(StockActionType.OPENING, input));
    }

    @PostMapping("/receipt")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:receipt")
    public WmStockActionVo receipt(@Valid @RequestBody StockCommand input) {
        return WmStockActionController.toView(service.execute(StockActionType.RECEIPT, input));
    }

    @PostMapping("/gain")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:gain")
    public WmStockActionVo gain(@Valid @RequestBody StockCommand input) {
        return WmStockActionController.toView(service.execute(StockActionType.GAIN, input));
    }

    @PostMapping("/loss")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:loss")
    public WmStockActionVo loss(@Valid @RequestBody StockCommand input) {
        return WmStockActionController.toView(service.execute(StockActionType.LOSS, input));
    }

    @PostMapping("/correction")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:correction")
    public WmStockActionVo correction(@Valid @RequestBody StockCommand input) {
        return WmStockActionController.toView(service.execute(StockActionType.CORRECTION, input));
    }

    @PostMapping("/return")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:stock:return")
    public WmStockActionVo returnMaterial(@Valid @RequestBody StockCommand input) {
        return WmStockActionController.toView(service.execute(StockActionType.RETURN, input));
    }
}
