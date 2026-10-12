// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.controller;

import dev.mesbase.business.qc.controller.vo.QcDefectPageVo;
import dev.mesbase.business.qc.controller.vo.QcDefectVo;
import dev.mesbase.business.qc.domain.QcDefectCreateCommand;
import dev.mesbase.business.qc.domain.QcDefectEntity;
import dev.mesbase.business.qc.domain.QcDefectPage;
import dev.mesbase.business.qc.domain.QcDefectQueryDto;
import dev.mesbase.business.qc.domain.QcDefectUpdateCommand;
import dev.mesbase.business.qc.service.IQcDefectService;
import dev.mesbase.business.qc.sync.QcDefectSyncResult;
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
 * 质量缺陷 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/qc/qc-defect")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcDefectController {
    private final IQcDefectService service;

    public QcDefectController(IQcDefectService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.LIST)
    public QcDefectPageVo list(@Valid @ModelAttribute QcDefectQueryDto query) {
        QcDefectPage page = service.selectPage(query);
        return new QcDefectPageVo(
                page.items().stream().map(QcDefectController::toView).toList(),
                page.total(), page.offset(), page.limit());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.QUERY)
    public QcDefectVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.CREATE)
    public QcDefectVo create(@Valid @RequestBody QcDefectCreateCommand input) {
        return toView(service.insert(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.UPDATE)
    public QcDefectVo update(@PathVariable Long id,
                             @Valid @RequestBody QcDefectUpdateCommand input) {
        return toView(service.updateById(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.DELETE)
    public void delete(@RequestBody
                       @NotEmpty @Size(max = 100)
                       List<@NotNull Long> ids) {
        service.deleteByIds(ids);
    }

    @PostMapping("/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.SYNC)
    public QcDefectSyncResult sync() {
        return service.sync(FactoryContext.required());
    }

    private static QcDefectVo toView(QcDefectEntity row) {
        return new QcDefectVo(
                row.getDefectId() == null ? null : row.getDefectId().toString(),
                row.getFactoryId(),
                row.getDefectCode(),
                row.getDefectName(),
                row.getIndexType(),
                row.getDefectLevel(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime()
        );
    }
}

