package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.MesProductionPlanItemPageVo;
import dev.mesbase.business.production.controller.vo.MesProductionPlanItemVo;
import dev.mesbase.business.production.domain.MesProductionPlanItemEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemQueryDto;
import dev.mesbase.business.production.service.IMesProductionPlanItemService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产计划明细 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-production-plan-item")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesProductionPlanItemController {
    private final IMesProductionPlanItemService service;

    public MesProductionPlanItemController(IMesProductionPlanItemService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanItemPermissions.LIST)
    public MesProductionPlanItemPageVo list(@Valid @ModelAttribute MesProductionPlanItemQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesProductionPlanItemPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesProductionPlanItemPermissions.QUERY)
    public MesProductionPlanItemVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    private static MesProductionPlanItemVo toView(MesProductionPlanItemEntity row) {
        return BeanConversions.convert(row, MesProductionPlanItemVo.class);
    }
}

