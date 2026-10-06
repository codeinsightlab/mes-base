package dev.mesbase.mes.pro.productionmonitor.controller;

import com.github.pagehelper.PageHelper;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.domain.dto.ProductionMonitorTimelineQueryDTO;
import dev.mesbase.mes.pro.productionmonitor.service.ProductionMonitorService;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorWorkorderVO;
import dev.mesbase.mes.pro.productionmonitor.domain.vo.ProductionMonitorTimelineEventVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import java.util.List;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.analytics.enabled",havingValue="true")
@RestController
@RequestMapping("/api/mes/pro/production-monitor")
@Validated
public class ProductionMonitorController extends BaseController {
    private final ProductionMonitorService service;

    public ProductionMonitorController(ProductionMonitorService service) {
        this.service = service;
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:productionMonitor:list")
    @GetMapping("/summary")
    public AjaxResult summary(@RequestParam(required = false) String month) {
        return AjaxResult.success(service.summary(month));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:productionMonitor:list")
    @GetMapping("/workorders")
    public TableDataInfo workorders(@Valid ProductionMonitorQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        if (pageSize > 100) pageSize = 100;
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        PageHelper.startPage(pageNum, pageSize);
        List<ProductionMonitorWorkorderVO> list = service.workorders(query);
        return getDataTable(list);
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:productionMonitor:query")
    @GetMapping("/workorders/{workorderId}")
    public AjaxResult detail(@PathVariable Long workorderId) {
        return AjaxResult.success(service.detail(workorderId));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:productionMonitor:query")
    @GetMapping("/workorders/{workorderId}/timeline")
    public TableDataInfo timeline(@PathVariable Long workorderId, @Valid ProductionMonitorTimelineQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        if (pageSize > 100) pageSize = 100;
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        List<ProductionMonitorTimelineEventVO> list = service.timeline(workorderId, query);
        return getDataTable(list);
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:productionMonitor:query")
    @GetMapping("/workorders/{workorderId}/flowcards/{xtransferNo}/routes/{routeId}")
    public AjaxResult routeTrack(@PathVariable Long workorderId, @PathVariable String xtransferNo,
                                 @PathVariable Long routeId) {
        return AjaxResult.success(service.routeTrack(workorderId, xtransferNo, routeId));
    }
}
