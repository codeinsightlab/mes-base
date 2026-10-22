package dev.mesbase.business.issue.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.issue.controller.vo.MesMaterialIssueItemPageVo;
import dev.mesbase.business.issue.controller.vo.MesMaterialIssueItemVo;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemUpdateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueItemService;
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
 * 领料出库明细 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/issue/mes-material-issue-item")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesMaterialIssueItemController {
    private final IMesMaterialIssueItemService service;

    public MesMaterialIssueItemController(IMesMaterialIssueItemService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssueItemPermissions.LIST)
    public MesMaterialIssueItemPageVo list(@Valid @ModelAttribute MesMaterialIssueItemQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesMaterialIssueItemPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssueItemPermissions.QUERY)
    public MesMaterialIssueItemVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssueItemPermissions.CREATE)
    public MesMaterialIssueItemVo create(@Valid @RequestBody MesMaterialIssueItemCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssueItemPermissions.UPDATE)
    public MesMaterialIssueItemVo update(@PathVariable Long id,
                                         @Valid @RequestBody MesMaterialIssueItemUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesMaterialIssueItemPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }


    private static MesMaterialIssueItemVo toView(MesMaterialIssueItemEntity row) {
        return BeanConversions.convert(row, MesMaterialIssueItemVo.class);
    }
}

