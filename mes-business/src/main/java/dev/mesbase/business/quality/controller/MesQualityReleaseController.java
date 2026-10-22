package dev.mesbase.business.quality.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.quality.controller.vo.MesQualityReleasePageVo;
import dev.mesbase.business.quality.controller.vo.MesQualityReleaseVo;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleaseQueryDto;
import dev.mesbase.business.quality.service.IMesQualityReleaseService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式质量放行事实 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/quality/mes-quality-release")
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesQualityReleaseController {
    private final IMesQualityReleaseService service;

    public MesQualityReleaseController(IMesQualityReleaseService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesQualityReleasePermissions.LIST)
    public MesQualityReleasePageVo list(@Valid @ModelAttribute MesQualityReleaseQueryDto query) {
        Page<MesQualityReleaseEntity> page = service.selectPage(query);
        return new MesQualityReleasePageVo(
                page.stream().map(MesQualityReleaseController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesQualityReleasePermissions.QUERY)
    public MesQualityReleaseVo detail(@PathVariable String id) {
        return toView(service.selectById(id));
    }


    private static MesQualityReleaseVo toView(MesQualityReleaseEntity row) {
        return new MesQualityReleaseVo(
                row.getId(),
                Objects.toString(row.getExecutionId(), null),
                Objects.toString(row.getSequenceNo(), null),
                row.getActionType(),
                Objects.toString(row.getQuantity(), null),
                row.getBasisHash(),
                row.getReferenceId(),
                row.getRequestHash(),
                Objects.toString(row.getOperatorUserId(), null),
                row.getRecordedAt(),
                row.getReason()
        );
    }
}

