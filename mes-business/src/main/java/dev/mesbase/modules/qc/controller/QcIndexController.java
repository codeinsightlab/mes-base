// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller;

import dev.mesbase.modules.qc.controller.vo.QcIndexPageVo;
import dev.mesbase.modules.qc.controller.vo.QcIndexVo;
import dev.mesbase.modules.qc.domain.QcIndexCreateCommand;
import dev.mesbase.modules.qc.domain.QcIndexEntity;
import dev.mesbase.modules.qc.domain.QcIndexPage;
import dev.mesbase.modules.qc.domain.QcIndexQueryDto;
import dev.mesbase.modules.qc.domain.QcIndexUpdateCommand;
import dev.mesbase.modules.qc.service.IQcIndexService;
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
 * 质量检测项 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/qc/qc-index")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcIndexController {
    private final IQcIndexService service;

    public QcIndexController(IQcIndexService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcIndexPermissions.READ)
    public QcIndexPageVo list(@Valid @ModelAttribute QcIndexQueryDto query) {
        QcIndexPage page = service.selectPage(query);
        return new QcIndexPageVo(
                page.items().stream().map(QcIndexController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcIndexPermissions.READ)
    public QcIndexVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcIndexPermissions.CREATE)
    public QcIndexVo create(@Valid @RequestBody QcIndexCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcIndexPermissions.UPDATE)
    public QcIndexVo update(@PathVariable Long id,
                            @Valid @RequestBody QcIndexUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcIndexPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    private static QcIndexVo toView(QcIndexEntity row) {
        return new QcIndexVo(
                row.getIndexId() == null ? null : row.getIndexId().toString(),
                row.getFactoryId(),
                row.getIndexCode(),
                row.getIndexName(),
                row.getIndexType(),
                row.getQcTool(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

