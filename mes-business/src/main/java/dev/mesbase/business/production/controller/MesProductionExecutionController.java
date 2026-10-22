package dev.mesbase.business.production.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import com.github.pagehelper.Page;
import java.util.Optional;

import java.util.Objects;

import dev.mesbase.business.production.controller.vo.MesProductionExecutionPageVo;
import dev.mesbase.business.production.controller.vo.MesProductionExecutionVo;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionQueryDto;
import dev.mesbase.business.production.domain.MesProductionExecutionUpdateCommand;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.service.ProductionExecutionQueryService;
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
 * 生产执行单元 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-production-execution")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesProductionExecutionController {
    private final IMesProductionExecutionService service;
    private final ProductionExecutionQueryService queries;

    public MesProductionExecutionController(IMesProductionExecutionService service,
                                            ProductionExecutionQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.LIST)
    public MesProductionExecutionPageVo list(@Valid @ModelAttribute MesProductionExecutionQueryDto query) {
        var data = queries.page(query);
        var page = data.records();
        return BeanConversions.convertPage(page, row -> {
                    var routing = data.references().routings().get(row.getRoutingId());
                    return toView(row, Optional.ofNullable(routing).map(value -> value.code()).orElse(null),
                            Optional.ofNullable(routing).map(value -> value.name()).orElse(null),
                            Optional.ofNullable(routing).map(value -> value.version()).orElse(null),
                            ProductionExecutionQueryService.name(data.references().users(), row.getCreateBy()),
                            ProductionExecutionQueryService.name(data.references().users(), row.getUpdateBy()),
                            data.references().workorderStatuses().get(row.getWorkorderId()));
                }, MesProductionExecutionPageVo::new);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.QUERY)
    public MesProductionExecutionVo detail(@PathVariable Long id) {
        return toView(queries.detail(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.CREATE)
    public MesProductionExecutionVo create(@Valid @RequestBody MesProductionExecutionCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.UPDATE)
    public MesProductionExecutionVo update(@PathVariable Long id,
                                           @Valid @RequestBody MesProductionExecutionUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    private record Labels(String routingCode, String routingName, Integer routingVersion,
                          String createByName, String updateByName, String workorderStatus) { }

    private static MesProductionExecutionVo toView(ProductionExecutionQueryService.Display display) {
        return toView(display.record(), Optional.ofNullable(display.routing()).map(nullableValue -> nullableValue
                .code()).orElse(null), Optional.ofNullable(display.routing()).map(nullableValue -> nullableValue
                .name()).orElse(null), Optional.ofNullable(display.routing()).map(nullableValue -> nullableValue
                .version()).orElse(null), display.createByName(), display.updateByName(), display.workorderStatus());
    }

    static MesProductionExecutionVo toView(MesProductionExecutionEntity row) {
        return toView(row, null, null, null, null, null, null);
    }

    private static MesProductionExecutionVo toView(MesProductionExecutionEntity row, String routingCode,
                                                   String routingName, Integer routingVersion, String createByName,
                                                   String updateByName, String workorderStatus) {
        return BeanConversions.convertWith(row, new Labels(routingCode, routingName, routingVersion,
                createByName, updateByName, workorderStatus), MesProductionExecutionVo.class);
    }
}

