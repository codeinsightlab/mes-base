package dev.mesbase.business.purchase.controller;

import dev.mesbase.business.purchase.controller.vo.PurchaseReferencePricePageVo;
import dev.mesbase.business.purchase.controller.vo.PurchaseReferencePriceVo;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceCreateCommand;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceEntity;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceQueryDto;
import dev.mesbase.business.purchase.service.IPurchaseReferencePriceService;
import dev.mesbase.common.utils.bean.BeanConversions;
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
 * 参考报价历史 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/purchase/purchase-reference-price")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PurchaseReferencePriceController {
    private final IPurchaseReferencePriceService service;

    public PurchaseReferencePriceController(IPurchaseReferencePriceService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PurchaseReferencePricePermissions.LIST)
    public PurchaseReferencePricePageVo list(@Valid @ModelAttribute PurchaseReferencePriceQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), PurchaseReferencePricePageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PurchaseReferencePricePermissions.QUERY)
    public PurchaseReferencePriceVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PurchaseReferencePricePermissions.CREATE)
    public PurchaseReferencePriceVo create(@Valid @RequestBody PurchaseReferencePriceCreateCommand input) {
        return toView(service.insert(input));
    }

    @GetMapping("/effective")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = PurchaseReferencePricePermissions.QUERY)
    public PurchaseReferencePriceVo effective(@org.springframework.web.bind.annotation.RequestParam Long supplierId,
                                              @org.springframework.web.bind.annotation.RequestParam Long materialId,
                                              @org.springframework.web.bind.annotation.RequestParam java.time.LocalDate date) {
        var row = service.effective(supplierId, materialId, date);
        if (row == null) return null;
        return toView(row);
    }

    private static PurchaseReferencePriceVo toView(PurchaseReferencePriceEntity row) {
        return BeanConversions.convert(row, PurchaseReferencePriceVo.class);
    }
}
