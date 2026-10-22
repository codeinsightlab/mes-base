package dev.mesbase.business.quality.controller;

import dev.mesbase.business.quality.domain.DefectStatistics.Query;
import dev.mesbase.business.quality.service.DefectStatisticsService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class DefectStatisticsController {
    private final DefectStatisticsService service;

    public DefectStatisticsController(DefectStatisticsService service) {
        this.service = service;
    }

    public record Row(String operationId, String defectTypeId, String typeName, Long typeNameVariants,
                      String wasteCategory,
                      String declaredQty, String independentQty, String overlapQty, String unverifiedQty,
                      String recoveredQty, String netDefectQty) {
    }

    public record Page(List<Row> items, long total, int offset, int limit) {
    }

    @GetMapping("/api/quality/defects/statistics")
    @ResponseBody
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:quality:defect:read")
    public Page statistics(@ModelAttribute Query query) {
        return BeanConversions.convertPage(service.summarize(query), Page.class);
    }
}
