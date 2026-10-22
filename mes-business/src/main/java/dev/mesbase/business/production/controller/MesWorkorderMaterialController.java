package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.controller.vo.MesWorkorderMaterialPageVo;
import dev.mesbase.business.production.controller.vo.MesWorkorderMaterialVo;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialQueryDto;
import dev.mesbase.business.production.service.IMesWorkorderMaterialService;
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
 * 本地工单用料需求 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/production/mes-workorder-material")
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesWorkorderMaterialController {
    private final IMesWorkorderMaterialService service;

    public MesWorkorderMaterialController(IMesWorkorderMaterialService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesWorkorderMaterialPermissions.LIST)
    public MesWorkorderMaterialPageVo list(@Valid @ModelAttribute MesWorkorderMaterialQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesWorkorderMaterialPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesWorkorderMaterialPermissions.QUERY)
    public MesWorkorderMaterialVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }


    private static MesWorkorderMaterialVo toView(MesWorkorderMaterialEntity row) {
        return BeanConversions.convert(row, MesWorkorderMaterialVo.class);
    }
}

