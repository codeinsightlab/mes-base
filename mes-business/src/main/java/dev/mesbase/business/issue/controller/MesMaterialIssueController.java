// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.controller;

import dev.mesbase.business.issue.controller.vo.MesMaterialIssuePageVo;
import dev.mesbase.business.issue.controller.vo.MesMaterialIssueVo;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssuePage;
import dev.mesbase.business.issue.domain.MesMaterialIssueQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueUpdateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueService;
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
 * 领料出库单 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/issue/mes-material-issue")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesMaterialIssueController {
    private final IMesMaterialIssueService service;

    public MesMaterialIssueController(IMesMaterialIssueService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssuePermissions.LIST)
    public MesMaterialIssuePageVo list(@Valid @ModelAttribute MesMaterialIssueQueryDto query) {
        MesMaterialIssuePage page = service.selectPage(query);
        return new MesMaterialIssuePageVo(
                page.items().stream().map(MesMaterialIssueController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssuePermissions.QUERY)
    public MesMaterialIssueVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssuePermissions.CREATE)
    public MesMaterialIssueVo create(@Valid @RequestBody MesMaterialIssueCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssuePermissions.UPDATE)
    public MesMaterialIssueVo update(@PathVariable Long id,
                                     @Valid @RequestBody MesMaterialIssueUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssuePermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    private static MesMaterialIssueVo toView(MesMaterialIssueEntity row) {
        return new MesMaterialIssueVo(
                row.getId() == null ? null : row.getId().toString(),
                row.getFactoryId(),
                row.getIssueNo(),
                row.getWorkorderId() == null ? null : row.getWorkorderId().toString(),
                row.getWorkorderCode(),
                row.getProductId() == null ? null : row.getProductId().toString(),
                row.getProductCode(),
                row.getFlowCardNo(),
                row.getRoutingId() == null ? null : row.getRoutingId().toString(),
                row.getIssueTime(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

