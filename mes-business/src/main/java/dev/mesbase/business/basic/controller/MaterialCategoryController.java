package dev.mesbase.business.basic.controller;

import dev.mesbase.business.basic.controller.vo.MaterialCategoryPageVo;
import dev.mesbase.business.basic.controller.vo.MaterialCategoryVo;
import dev.mesbase.business.basic.domain.MaterialCategoryCreateCommand;
import dev.mesbase.business.basic.domain.MaterialCategoryEntity;
import dev.mesbase.business.basic.domain.MaterialCategoryPage;
import dev.mesbase.business.basic.domain.MaterialCategoryQueryDto;
import dev.mesbase.business.basic.domain.MaterialCategoryUpdateCommand;
import dev.mesbase.business.basic.service.IMaterialCategoryService;
import dev.mesbase.business.basic.sync.MaterialCategorySyncResult;
import dev.mesbase.platform.execution.FactoryContext;
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
 * 物料产品分类 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/basic/material-category")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialCategoryController {
    private final IMaterialCategoryService service;

    public MaterialCategoryController(IMaterialCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialCategoryPermissions.LIST)
    public MaterialCategoryPageVo list(@Valid @ModelAttribute MaterialCategoryQueryDto query) {
        MaterialCategoryPage page = service.selectPage(query);
        return new MaterialCategoryPageVo(
                page.items().stream().map(MaterialCategoryController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialCategoryPermissions.QUERY)
    public MaterialCategoryVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialCategoryPermissions.CREATE)
    public MaterialCategoryVo create(@Valid @RequestBody MaterialCategoryCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialCategoryPermissions.UPDATE)
    public MaterialCategoryVo update(@PathVariable Long id,
            @Valid @RequestBody MaterialCategoryUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialCategoryPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MaterialCategoryPermissions.SYNC)
    public MaterialCategorySyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static MaterialCategoryVo toView(MaterialCategoryEntity row) {
        return new MaterialCategoryVo(
                row.getId() == null ? null : row.getId().toString(),

                row.getCode(),
                row.getName(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getSort(),
                row.getStatus(),
                row.getRemark()
        );
    }
}

