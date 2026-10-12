// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.controller;

import dev.mesbase.modules.md.controller.vo.MdUnitMeasurePageVo;
import dev.mesbase.modules.md.controller.vo.MdUnitMeasureVo;
import dev.mesbase.modules.md.domain.MdUnitMeasureCreateCommand;
import dev.mesbase.modules.md.domain.MdUnitMeasureEntity;
import dev.mesbase.modules.md.domain.MdUnitMeasurePage;
import dev.mesbase.modules.md.domain.MdUnitMeasureQueryDto;
import dev.mesbase.modules.md.domain.MdUnitMeasureUpdateCommand;
import dev.mesbase.modules.md.service.IMdUnitMeasureService;
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
 * 计量单位 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/md/md-unit-measure")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdUnitMeasureController {
    private final IMdUnitMeasureService service;

    public MdUnitMeasureController(IMdUnitMeasureService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdUnitMeasurePermissions.READ)
    public MdUnitMeasurePageVo list(@Valid @ModelAttribute MdUnitMeasureQueryDto query) {
        MdUnitMeasurePage page = service.selectPage(query);
        return new MdUnitMeasurePageVo(
                page.items().stream().map(MdUnitMeasureController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdUnitMeasurePermissions.READ)
    public MdUnitMeasureVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdUnitMeasurePermissions.CREATE)
    public MdUnitMeasureVo create(@Valid @RequestBody MdUnitMeasureCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdUnitMeasurePermissions.UPDATE)
    public MdUnitMeasureVo update(@PathVariable Long id,
                                  @Valid @RequestBody MdUnitMeasureUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdUnitMeasurePermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    private static MdUnitMeasureVo toView(MdUnitMeasureEntity row) {
        return new MdUnitMeasureVo(
                row.getMeasureId() == null ? null : row.getMeasureId().toString(),
                row.getFactoryId(),
                row.getMeasureCode(),
                row.getMeasureName(),
                row.getPrimaryFlag(),
                row.getPrimaryId() == null ? null : row.getPrimaryId().toString(),
                row.getChangeRate() == null ? null : row.getChangeRate().toString(),
                row.getEnableFlag(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

