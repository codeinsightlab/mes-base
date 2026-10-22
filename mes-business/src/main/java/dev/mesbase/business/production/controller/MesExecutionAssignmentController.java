package dev.mesbase.business.production.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.production.controller.vo.MesExecutionAssignmentPageVo;
import dev.mesbase.business.production.controller.vo.MesExecutionAssignmentVo;
import dev.mesbase.business.production.domain.MesExecutionAssignmentCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionAssignmentEntity;
import dev.mesbase.business.production.domain.MesExecutionAssignmentQueryDto;
import dev.mesbase.business.production.domain.MesExecutionAssignmentUpdateCommand;
import dev.mesbase.business.production.service.IMesExecutionAssignmentService;
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
 * 生产节点派工 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-execution-assignment")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesExecutionAssignmentController {
    private final IMesExecutionAssignmentService service;

    public MesExecutionAssignmentController(IMesExecutionAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionAssignmentPermissions.LIST)
    public MesExecutionAssignmentPageVo list(@Valid @ModelAttribute MesExecutionAssignmentQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesExecutionAssignmentPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionAssignmentPermissions.QUERY)
    public MesExecutionAssignmentVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionAssignmentPermissions.CREATE)
    public MesExecutionAssignmentVo create(@Valid @RequestBody MesExecutionAssignmentCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionAssignmentPermissions.UPDATE)
    public MesExecutionAssignmentVo update(@PathVariable Long id,
                                           @Valid @RequestBody MesExecutionAssignmentUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionAssignmentPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    private static MesExecutionAssignmentVo toView(MesExecutionAssignmentEntity row) {
        return BeanConversions.convert(row, MesExecutionAssignmentVo.class);
    }
}

