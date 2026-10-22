package dev.mesbase.business.production.controller;

import com.github.pagehelper.Page;
import java.util.Objects;

import dev.mesbase.business.production.controller.vo.MesWorkorderMaterialPageVo;
import dev.mesbase.business.production.controller.vo.MesWorkorderMaterialVo;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialQueryDto;
import dev.mesbase.business.production.service.IMesWorkorderMaterialService;
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
        Page<MesWorkorderMaterialEntity> page = service.selectPage(query);
        return new MesWorkorderMaterialPageVo(
                page.stream().map(MesWorkorderMaterialController::toView).toList(),
                page.getTotal(), (int) page.getStartRow(), page.getPageSize());
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesWorkorderMaterialPermissions.QUERY)
    public MesWorkorderMaterialVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }


    private static MesWorkorderMaterialVo toView(MesWorkorderMaterialEntity row) {
        return new MesWorkorderMaterialVo(
                Objects.toString(row.getId(), null),
                Objects.toString(row.getMaterialId(), null),
                Objects.toString(row.getUnitId(), null),
                Objects.toString(row.getTotalRequiredQty(), null),
                Objects.toString(row.getPlannedQty(), null),
                row.getWarehouseIds(),
                row.getWorkCenter(),
                Objects.toString(row.getRevision(), null),
                row.getStatus(),
                Objects.toString(row.getUpdatedBy(), null),
                row.getUpdatedAt()
        );
    }
}

