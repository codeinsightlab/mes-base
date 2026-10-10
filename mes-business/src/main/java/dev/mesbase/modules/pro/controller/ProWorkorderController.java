// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.controller;

import dev.mesbase.modules.pro.controller.vo.ProWorkorderPageVo;
import dev.mesbase.modules.pro.controller.vo.ProWorkorderVo;
import dev.mesbase.modules.pro.domain.ProWorkorderCreateCommand;
import dev.mesbase.modules.pro.domain.ProWorkorderEntity;
import dev.mesbase.modules.pro.domain.ProWorkorderPage;
import dev.mesbase.modules.pro.domain.ProWorkorderQueryDto;
import dev.mesbase.modules.pro.domain.ProWorkorderUpdateCommand;
import dev.mesbase.modules.pro.service.IProWorkorderService;
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
 * 生产工单表 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/pro/pro-workorder")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProWorkorderController {
    private final IProWorkorderService service;

    public ProWorkorderController(IProWorkorderService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = ProWorkorderPermissions.READ)
    public ProWorkorderPageVo list(@Valid @ModelAttribute ProWorkorderQueryDto query) {
        ProWorkorderPage page = service.selectPage(query);
        return new ProWorkorderPageVo(
                page.items().stream().map(ProWorkorderController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = ProWorkorderPermissions.READ)
    public ProWorkorderVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = ProWorkorderPermissions.CREATE)
    public ProWorkorderVo create(@Valid @RequestBody ProWorkorderCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = ProWorkorderPermissions.UPDATE)
    public ProWorkorderVo update(@PathVariable Long id,
                                 @Valid @RequestBody ProWorkorderUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = ProWorkorderPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    private static ProWorkorderVo toView(ProWorkorderEntity row) {
        return new ProWorkorderVo(
                row.getWorkorderId() == null ? null : row.getWorkorderId().toString(),
                row.getWorkorderCode(),
                row.getWorkorderName(),
                row.getOrderSource(),
                row.getSourceCode(),
                row.getProductId() == null ? null : row.getProductId().toString(),
                row.getProductCode(),
                row.getProductName(),
                row.getProductSpc(),
                row.getUnitOfMeasure(),
                row.getQuantity(),
                row.getQuantityProduced(),
                row.getQuantityChanged(),
                row.getQuantityScheduled(),
                row.getClientId() == null ? null : row.getClientId().toString(),
                row.getClientCode(),
                row.getClientName(),
                row.getBatchCode(),
                row.getRequestDate(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getAncestors(),
                row.getFinishDate(),
                row.getStatus(),
                row.getRemark(),
                row.getAttr1(),
                row.getAttr2(),
                row.getAttr3(),
                row.getAttr4(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getTransactionDate(),
                row.getMoId()
        );
    }
}

