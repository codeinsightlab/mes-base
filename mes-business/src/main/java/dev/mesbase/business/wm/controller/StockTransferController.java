package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.service.StockTransferService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wm/stock-transfers")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class StockTransferController {
    private static final String PERMISSION = "business:wm:stock:document:";
    private final StockTransferService service;

    public StockTransferController(StockTransferService service) {
        this.service = service;
    }

    public record Detail(StockDocumentController.Detail outbound, StockDocumentController.Detail inbound) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "list")
    public StockDocumentController.Page page(@ModelAttribute StockTransferService.Query query) {
        return BeanConversions.convertPage(service.page(query), StockDocumentController.Page.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "query")
    public Detail detail(@PathVariable Long id) {
        return BeanConversions.convert(service.detailByDocumentId(id), Detail.class);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "create")
    public Detail create(@Valid @RequestBody StockTransferService.Save command) {
        return BeanConversions.convert(service.create(command), Detail.class);
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "update")
    public Detail update(@PathVariable Long id, @Valid @RequestBody StockTransferService.Save command) {
        return BeanConversions.convert(service.update(id, command), Detail.class);
    }

    @PostMapping("/{id}/actions/approve")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "post")
    public Detail approve(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Post command) {
        return BeanConversions.convert(service.approve(id, command), Detail.class);
    }

    @PostMapping("/{id}/actions/cancel")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERMISSION + "cancel")
    public Detail cancel(@PathVariable Long id, @Valid @RequestBody StockDocumentModels.Version command) {
        return BeanConversions.convert(service.cancel(id, command), Detail.class);
    }
}
