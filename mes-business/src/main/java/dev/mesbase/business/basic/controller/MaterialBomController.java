package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.controller.vo.MaterialBomPageVo;
import dev.mesbase.business.basic.controller.vo.MaterialBomExplosionVo;
import dev.mesbase.business.basic.controller.vo.MaterialBomGraphVo;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.RequestParam;
import dev.mesbase.business.basic.controller.vo.MaterialBomVo;
import dev.mesbase.business.basic.domain.MaterialBomCreateCommand;
import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.business.basic.domain.MaterialBomQueryDto;
import dev.mesbase.business.basic.domain.MaterialBomUpdateCommand;
import dev.mesbase.business.basic.service.MaterialBomService;
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
 * 物料组成版本 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/material-bom")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialBomController {
    private final MaterialBomService service;

    public MaterialBomController(MaterialBomService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomPermissions.LIST)
    public MaterialBomPageVo list(@Valid @ModelAttribute MaterialBomQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MaterialBomPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomPermissions.QUERY)
    public MaterialBomVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @GetMapping("/default")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.QUERY)
    public MaterialBomVo defaultVersion(@RequestParam Long productId) {
        var row = service.selectDefault(productId);
        if (row == null) return null;
        return toView(row);
    }

    @GetMapping("/explode")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.QUERY)
    public MaterialBomExplosionVo explode(@RequestParam String materialCode,
                                          @RequestParam(required = false) Long bomId,
                                          @RequestParam(defaultValue = "1") BigDecimal quantity,
                                          @RequestParam(defaultValue = "0") int versionOffset,
                                          @RequestParam(defaultValue = "20") int versionLimit) {
        return BeanConversions.convert(service.explode(materialCode, bomId, quantity, versionOffset, versionLimit),
                MaterialBomExplosionVo.class);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomPermissions.CREATE)
    public MaterialBomVo create(@Valid @RequestBody MaterialBomCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomPermissions.UPDATE)
    public MaterialBomVo update(@PathVariable Long id,
                                @Valid @RequestBody MaterialBomUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    public record Transition(long revision) {
    }

    @PostMapping("/{id}/publish")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.UPDATE)
    public MaterialBomVo publish(@PathVariable Long id, @RequestBody Transition command) {
        return toView(service.publish(id, command.revision()));
    }

    @PostMapping("/{id}/disable")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialPermissions.UPDATE)
    public MaterialBomVo disable(@PathVariable Long id, @RequestBody Transition command) {
        return toView(service.disable(id, command.revision()));
    }

    @PostMapping("/{id}/default")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialBomPermissions.UPDATE)
    public MaterialBomVo setDefault(@PathVariable Long id, @RequestBody Transition command) {
        return toView(service.setDefault(id, command.revision()));
    }

    @GetMapping("/{id}/structure")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MaterialPermissions.QUERY)
    public MaterialBomGraphVo structure(@PathVariable Long id, @RequestParam BigDecimal quantity) {
        return BeanConversions.convert(service.graph(id, quantity), MaterialBomGraphVo.class);
    }

    private static MaterialBomVo toView(MaterialBomEntity row) {
        return BeanConversions.convert(row, MaterialBomVo.class);
    }
}
