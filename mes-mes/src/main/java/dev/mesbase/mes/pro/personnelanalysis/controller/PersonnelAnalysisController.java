package dev.mesbase.mes.pro.personnelanalysis.controller;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.mes.pro.domain.PageResult;
import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelAnalysisQueryDTO;
import dev.mesbase.mes.pro.personnelanalysis.service.PersonnelAnalysisService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.analytics.enabled",havingValue="true")
@RestController
@RequestMapping("/api/mes/pro/personnel-analysis")
@Validated
public class PersonnelAnalysisController {
    private final PersonnelAnalysisService service;
    public PersonnelAnalysisController(PersonnelAnalysisService service) { this.service = service; }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:personnelAnalysis:list")
    @GetMapping("/accounts")
    public TableDataInfo accounts(@Valid PersonnelAnalysisQueryDTO request) {
        return table(service.accounts(request));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:personnelAnalysis:query")
    @GetMapping("/accounts/{userId}/overview")
    public AjaxResult overview(@PathVariable Long userId, @Valid PersonnelAnalysisQueryDTO request) {
        request.setUserId(userId);
        return AjaxResult.success(service.overview(request));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:personnelAnalysis:query")
    @GetMapping("/accounts/{userId}/processes")
    public TableDataInfo processes(@PathVariable Long userId, @Valid PersonnelAnalysisQueryDTO request) {
        request.setUserId(userId);
        return table(service.processes(request));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:personnelAnalysis:query")
    @GetMapping("/accounts/{userId}/products")
    public TableDataInfo products(@PathVariable Long userId, @Valid PersonnelAnalysisQueryDTO request) {
        request.setUserId(userId);
        return table(service.products(request));
    }

    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:personnelAnalysis:query")
    @GetMapping("/accounts/{userId}/trend")
    public AjaxResult trend(@PathVariable Long userId, @Valid PersonnelAnalysisQueryDTO request) {
        request.setUserId(userId);
        return AjaxResult.success(service.trend(request));
    }

    @GetMapping("/accounts/{userId}/records")
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="mes:pro:reportingWork:list")
    public TableDataInfo records(@PathVariable Long userId,@Valid PersonnelAnalysisQueryDTO request,
        @org.springframework.web.bind.annotation.RequestParam(required=false) Long processId,
        @org.springframework.web.bind.annotation.RequestParam(required=false) String itemCode) {
        request.setUserId(userId);var page=service.records(request,processId,itemCode);
        var table=new TableDataInfo();table.setCode(200);table.setRows(page.getList());table.setTotal(page.getTotal());return table;
    }

    private TableDataInfo table(PageResult<?> page) {
        TableDataInfo result = new TableDataInfo();
        result.setCode(200);
        result.setMsg("查询成功");
        result.setRows(page.getRows());
        result.setTotal(page.getTotal());
        return result;
    }
}
