// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.MesProductionExecutionPageVo;
import dev.mesbase.business.production.controller.vo.MesProductionExecutionVo;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionPage;
import dev.mesbase.business.production.domain.MesProductionExecutionQueryDto;
import dev.mesbase.business.production.domain.MesProductionExecutionUpdateCommand;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
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

    public MesProductionExecutionController(IMesProductionExecutionService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.LIST)
    public MesProductionExecutionPageVo list(@Valid @ModelAttribute MesProductionExecutionQueryDto query) {
        MesProductionExecutionPage page = service.selectPage(query);
        return new MesProductionExecutionPageVo(
                page.items().stream().map(MesProductionExecutionController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionExecutionPermissions.QUERY)
    public MesProductionExecutionVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
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


    private static MesProductionExecutionVo toView(MesProductionExecutionEntity row) {
        return new MesProductionExecutionVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getExecutionNo(),
                row.getWorkorderId() == null ? null : row.getWorkorderId().toString(),
                row.getProductId() == null ? null : row.getProductId().toString(),
                row.getRoutingId() == null ? null : row.getRoutingId().toString(),
                row.getTargetQty() == null ? null : row.getTargetQty().toString(),
                row.getStatus(),
                row.getStartTime(),
                row.getEndTime(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

