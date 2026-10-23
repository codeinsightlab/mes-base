package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.controller.vo.MaterialBomItemPageVo;
import dev.mesbase.business.basic.controller.vo.MaterialBomItemVo;
import dev.mesbase.business.basic.domain.MaterialBomItemCreateCommand;
import dev.mesbase.business.basic.domain.MaterialBomItemEntity;
import dev.mesbase.business.basic.domain.MaterialBomItemQueryDto;
import dev.mesbase.business.basic.domain.MaterialBomItemUpdateCommand;
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
 * 组成组件 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/material-bom-item")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialBomItemController {
    private final MaterialBomService service;

    public MaterialBomItemController(MaterialBomService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomItemPermissions.LIST)
    public MaterialBomItemPageVo list(@Valid @ModelAttribute MaterialBomItemQueryDto query) {
        return BeanConversions.convertPage(service.itemPage(query), MaterialBomItemPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomItemPermissions.QUERY)
    public MaterialBomItemVo detail(@PathVariable Long id) {
        return toView(service.item(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomItemPermissions.CREATE)
    public MaterialBomItemVo create(@Valid @RequestBody MaterialBomItemCreateCommand input) {
        return toView(service.insertItem(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomItemPermissions.UPDATE)
    public MaterialBomItemVo update(@PathVariable Long id,
                                    @Valid @RequestBody MaterialBomItemUpdateCommand input) {
        return toView(service.updateItem(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialBomItemPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteItems(ids);
    }


    private static MaterialBomItemVo toView(MaterialBomItemEntity row) {
        return BeanConversions.convert(row, MaterialBomItemVo.class);
    }
}
