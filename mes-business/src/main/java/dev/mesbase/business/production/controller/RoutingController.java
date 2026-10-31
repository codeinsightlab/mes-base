package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.RoutingDetailVo;
import dev.mesbase.business.production.controller.vo.RoutingEdgeVo;
import dev.mesbase.business.production.controller.vo.RoutingNodeVo;
import dev.mesbase.business.production.controller.vo.RoutingPageVo;
import dev.mesbase.business.production.controller.vo.RoutingProductVo;
import dev.mesbase.business.production.controller.vo.RoutingReferenceVo;
import dev.mesbase.business.production.controller.vo.RoutingVo;
import dev.mesbase.business.production.domain.RoutingContentCommand;
import dev.mesbase.business.production.domain.RoutingCopyCommand;
import dev.mesbase.business.production.domain.RoutingCreateCommand;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingQueryDto;
import dev.mesbase.business.production.domain.RoutingReference;
import dev.mesbase.business.production.domain.RoutingRevisionCommand;
import dev.mesbase.business.production.domain.RoutingUpdateCommand;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.sync.ProductionSyncResult;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工艺路线 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/routing")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class RoutingController {
    private final IRoutingService service;

    public RoutingController(IRoutingService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = RoutingPermissions.LIST)
    public RoutingPageVo list(@Valid @ModelAttribute RoutingQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), RoutingPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = RoutingPermissions.QUERY)
    public RoutingDetailVo detail(@PathVariable Long id) {
        return detailView(service.detail(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = RoutingPermissions.CREATE)
    public RoutingVo create(@Valid @RequestBody RoutingCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = RoutingPermissions.UPDATE)
    public RoutingVo update(@PathVariable Long id,
                            @Valid @RequestBody RoutingUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = RoutingPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    @PutMapping("/{id}/content")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.UPDATE)
    public RoutingDetailVo saveContent(@PathVariable Long id, @Valid @RequestBody RoutingContentCommand input) {
        return detailView(service.saveContent(id, input));
    }

    @PostMapping("/{id}/actions/publish")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.PUBLISH)
    public RoutingVo publish(@PathVariable Long id, @Valid @RequestBody RoutingRevisionCommand input) {
        return toView(service.publish(id, input.revision()));
    }

    @PostMapping("/{id}/actions/disable")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.PUBLISH)
    public RoutingVo disable(@PathVariable Long id, @Valid @RequestBody RoutingRevisionCommand input) {
        return toView(service.availability(id, input.revision(), false));
    }

    @PostMapping("/{id}/actions/enable")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.PUBLISH)
    public RoutingVo enable(@PathVariable Long id, @Valid @RequestBody RoutingRevisionCommand input) {
        return toView(service.availability(id, input.revision(), true));
    }

    @PostMapping("/{id}/actions/new-version")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.VERSION)
    public RoutingVo newVersion(@PathVariable Long id, @Valid @RequestBody RoutingRevisionCommand input) {
        return toView(service.newVersion(id, input.revision()));
    }

    @PostMapping("/{id}/actions/copy")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.COPY)
    public RoutingVo copy(@PathVariable Long id, @Valid @RequestBody RoutingCopyCommand input) {
        return toView(service.copy(id, input));
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.SYNC)
    public ProductionSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    @GetMapping("/references/operations")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.QUERY)
    public List<RoutingReferenceVo> operations(@RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "100") int limit) {
        return service.operationOptions(search, limit).stream().map(RoutingController::referenceView).toList();
    }

    @GetMapping("/references/materials")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.QUERY)
    public List<RoutingReferenceVo> materials(@RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "100") int limit) {
        return service.materialOptions(search, limit).stream().map(RoutingController::referenceView).toList();
    }

    private static RoutingReferenceVo referenceView(RoutingReference row) {
        return new RoutingReferenceVo(row.id().toString(), row.code(), row.name());
    }

    private record CurrentProductNames(String currentMaterialCode, String currentMaterialName) {
    }

    static RoutingDetailVo detailView(RoutingDetail detail) {
        if (detail == null) return null;
        var nodeCodes = new HashMap<Long, String>();
        for (var node : detail.nodes()) nodeCodes.put(node.getId(), node.getNodeCode());
        return new RoutingDetailVo(toView(detail.routing()),
                detail.products().stream().map(row -> {
                    var current = detail.currentProducts().get(row.getMaterialId());
                    var names = new CurrentProductNames(null, null);
                    if (current != null) names = new CurrentProductNames(current.getCode(), current.getName());
                    return BeanConversions.convertWith(row, names, RoutingProductVo.class);
                }).toList(),
                detail.nodes().stream().map(row -> new RoutingNodeVo(row.getId().toString(), row.getNodeCode(),
                                row.getOperationId().toString(), row.getDisplayOrder(), row.getOperationCode(),
                                row.getOperationName(), row.getOperationDescription(), row.getIsKey(), row.getDescription()))
                        .toList(),
                detail.edges().stream().map(row -> new RoutingEdgeVo(row.getId().toString(),
                        nodeCodes.get(row.getFromNodeId()), nodeCodes.get(row.getToNodeId()))).toList());
    }

    private static RoutingVo toView(RoutingEntity row) {
        return BeanConversions.convert(row, RoutingVo.class);
    }
}

