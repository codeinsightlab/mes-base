package dev.mesbase.business.production.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.production.controller.vo.MesExecutionCompletionPageVo;
import dev.mesbase.business.production.controller.vo.MesExecutionCompletionVo;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionQueryDto;
import dev.mesbase.business.production.service.IMesExecutionCompletionService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式生产完工事实 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-execution-completion")
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesExecutionCompletionController {
    private final IMesExecutionCompletionService service;

    public MesExecutionCompletionController(IMesExecutionCompletionService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionCompletionPermissions.LIST)
    public MesExecutionCompletionPageVo list(@Valid @ModelAttribute MesExecutionCompletionQueryDto query) {
        Page<MesExecutionCompletionEntity> page = service.selectPage(query);
        return new MesExecutionCompletionPageVo(
                page.stream().map(MesExecutionCompletionController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesExecutionCompletionPermissions.QUERY)
    public MesExecutionCompletionVo detail(@PathVariable String id) {
        return toView(service.selectById(id));
    }


    private static MesExecutionCompletionVo toView(MesExecutionCompletionEntity row) {
        return new MesExecutionCompletionVo(
                row.getId(),
                Objects.toString(row.getExecutionId(), null),
                Objects.toString(row.getEffectiveQty(), null),
                Objects.toString(row.getReleasedQty(), null),
                Objects.toString(row.getReceiptQty(), null),
                Objects.toString(row.getAcceptedQty(), null),
                row.getBasisHash(),
                row.getRequestHash(),
                Objects.toString(row.getOperatorUserId(), null),
                row.getRecordedAt(),
                row.getReason()
        );
    }
}

