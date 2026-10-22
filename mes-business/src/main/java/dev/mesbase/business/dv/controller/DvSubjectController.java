package dev.mesbase.business.dv.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.dv.controller.vo.DvSubjectPageVo;
import dev.mesbase.business.dv.controller.vo.DvSubjectVo;
import dev.mesbase.business.dv.domain.DvSubjectCreateCommand;
import dev.mesbase.business.dv.domain.DvSubjectEntity;
import dev.mesbase.business.dv.domain.DvSubjectQueryDto;
import dev.mesbase.business.dv.domain.DvSubjectUpdateCommand;
import dev.mesbase.business.dv.service.IDvSubjectService;
import dev.mesbase.business.dv.sync.DvSubjectSyncResult;
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
 * 设备点检保养项目 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/dv/dv-subject")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class DvSubjectController {
    private final IDvSubjectService service;

    public DvSubjectController(IDvSubjectService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.LIST)
    public DvSubjectPageVo list(@Valid @ModelAttribute DvSubjectQueryDto query) {
        Page<DvSubjectEntity> page = service.selectPage(query);
        return new DvSubjectPageVo(
                page.stream().map(DvSubjectController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.QUERY)
    public DvSubjectVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.CREATE)
    public DvSubjectVo create(@Valid @RequestBody DvSubjectCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.UPDATE)
    public DvSubjectVo update(@PathVariable Long id,
                              @Valid @RequestBody DvSubjectUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.SYNC)
    public DvSubjectSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static DvSubjectVo toView(DvSubjectEntity row) {
        return new DvSubjectVo(
                Objects.toString(row.getSubjectId(), null),

                row.getSubjectCode(),
                row.getSubjectName(),
                row.getSubjectType(),
                row.getSubjectContent(),
                row.getSubjectStandard(),
                row.getEnableFlag(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

