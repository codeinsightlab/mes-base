package dev.mesbase.business.sales.controller;

import dev.mesbase.business.sales.controller.vo.MesSalesOrderPageVo;
import dev.mesbase.business.sales.controller.vo.MesSalesOrderVo;
import dev.mesbase.business.sales.controller.vo.MesSalesOrderItemVo;
import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderQueryDto;
import dev.mesbase.business.sales.domain.SalesOrderCreateRequest;
import dev.mesbase.business.sales.domain.SalesOrderDocument;
import dev.mesbase.business.sales.domain.SalesOrderUpdateRequest;
import dev.mesbase.business.sales.service.SalesOrderDocumentService;
import dev.mesbase.business.sales.controller.vo.SalesOrderDocumentVo;
import dev.mesbase.business.sales.service.IMesSalesOrderService;
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
 * 销售订单 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/sales/mes-sales-order")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesSalesOrderController {
    private final IMesSalesOrderService service;
    private final SalesOrderDocumentService documents;

    public MesSalesOrderController(IMesSalesOrderService service, SalesOrderDocumentService documents) {
        this.service = service;
        this.documents = documents;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderPermissions.LIST)
    public MesSalesOrderPageVo list(@Valid @ModelAttribute MesSalesOrderQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesSalesOrderPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderPermissions.QUERY)
    public MesSalesOrderVo detail(@PathVariable Long id) {
        return toView(documents.get(id).header());
    }

    @GetMapping("/{id}/document")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderPermissions.QUERY)
    public SalesOrderDocumentVo document(@PathVariable Long id) {
        return toDocument(documents.get(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderPermissions.CREATE)
    public SalesOrderDocumentVo create(@Valid @RequestBody SalesOrderCreateRequest input) {
        return toDocument(documents.create(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderPermissions.UPDATE)
    public SalesOrderDocumentVo update(@PathVariable Long id,
            @Valid @RequestBody SalesOrderUpdateRequest input) {
        return toDocument(documents.update(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesSalesOrderPermissions.DELETE)
    public void delete(@RequestBody @NotEmpty @Size(max = 100) List<@NotNull Long> ids) {
        documents.delete(ids);
    }


    private static MesSalesOrderVo toView(MesSalesOrderEntity row) {
        return BeanConversions.convert(row, MesSalesOrderVo.class);
    }

    private static SalesOrderDocumentVo toDocument(SalesOrderDocument document) {
        return new SalesOrderDocumentVo(toView(document.header()), document.items().stream()
                .map(row -> BeanConversions.convert(row, MesSalesOrderItemVo.class))
                .toList());
    }
}

