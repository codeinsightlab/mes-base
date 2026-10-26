package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.MesProductionPlanPageVo;
import dev.mesbase.business.production.controller.vo.MesProductionPlanVo;
import dev.mesbase.business.production.controller.vo.MesProductionPlanItemVo;
import dev.mesbase.business.production.controller.vo.ProductionPlanDocumentVo;
import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.business.production.domain.MesProductionPlanQueryDto;
import dev.mesbase.business.production.domain.ProductionPlanCreateRequest;
import dev.mesbase.business.production.domain.ProductionPlanDocument;
import dev.mesbase.business.production.domain.ProductionPlanUpdateRequest;
import dev.mesbase.business.production.service.IMesProductionPlanService;
import dev.mesbase.business.production.service.ProductionPlanDocumentService;
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
 * 生产计划 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-production-plan")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesProductionPlanController {
    private final IMesProductionPlanService service;
    private final ProductionPlanDocumentService documents;

    public MesProductionPlanController(IMesProductionPlanService service,
            ProductionPlanDocumentService documents) {
        this.service = service;
        this.documents = documents;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanPermissions.LIST)
    public MesProductionPlanPageVo list(@Valid @ModelAttribute MesProductionPlanQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesProductionPlanPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanPermissions.QUERY)
    public MesProductionPlanVo detail(@PathVariable Long id) {
        return toView(documents.get(id).header());
    }

    @GetMapping("/{id}/document")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanPermissions.QUERY)
    public ProductionPlanDocumentVo document(@PathVariable Long id) {
        return toDocument(documents.get(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanPermissions.CREATE)
    public ProductionPlanDocumentVo create(@Valid @RequestBody ProductionPlanCreateRequest input) {
        return toDocument(documents.create(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanPermissions.UPDATE)
    public ProductionPlanDocumentVo update(@PathVariable Long id,
            @Valid @RequestBody ProductionPlanUpdateRequest input) {
        return toDocument(documents.update(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull Long> ids) {
        documents.delete(ids);
    }


    private static MesProductionPlanVo toView(MesProductionPlanEntity row) {
        return BeanConversions.convert(row, MesProductionPlanVo.class);
    }

    private static ProductionPlanDocumentVo toDocument(ProductionPlanDocument document) {
        return new ProductionPlanDocumentVo(toView(document.header()), document.items().stream()
                .map(row -> BeanConversions.convert(row, MesProductionPlanItemVo.class))
                .toList());
    }
}

