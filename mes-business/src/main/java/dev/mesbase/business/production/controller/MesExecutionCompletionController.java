package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.MesExecutionCompletionPageVo;
import dev.mesbase.business.production.controller.vo.MesExecutionCompletionVo;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionPage;
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
        MesExecutionCompletionPage page = service.selectPage(query);
        return new MesExecutionCompletionPageVo(
                page.items().stream().map(MesExecutionCompletionController::toView).toList(),
                page.total(), page.offset(), page.limit());
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
                row.getExecutionId() == null ? null : row.getExecutionId().toString(),
                row.getEffectiveQty() == null ? null : row.getEffectiveQty().toString(),
                row.getReleasedQty() == null ? null : row.getReleasedQty().toString(),
                row.getReceiptQty() == null ? null : row.getReceiptQty().toString(),
                row.getAcceptedQty() == null ? null : row.getAcceptedQty().toString(),
                row.getBasisHash(),
                row.getRequestHash(),
                row.getOperatorUserId() == null ? null : row.getOperatorUserId().toString(),
                row.getRecordedAt(),
                row.getReason()
        );
    }
}

