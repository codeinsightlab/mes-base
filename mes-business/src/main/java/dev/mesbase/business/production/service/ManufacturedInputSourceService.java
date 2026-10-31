package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialBomItemMapper;
import dev.mesbase.business.basic.domain.MaterialBomItemExample;
import dev.mesbase.business.production.dao.ManufacturedInputSourceExtraMapper;
import dev.mesbase.business.production.domain.ManufacturedInputSource;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.common.core.page.PageData;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Formal manufacturing sources for the explicitly selected direct child BOM version.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ManufacturedInputSourceService {
    private final BusinessSourceRules sources;
    private final WorkorderMaterialService requirements;
    private final MaterialBomItemMapper components;
    private final ManufacturedInputSourceExtraMapper mapper;

    public ManufacturedInputSourceService(BusinessSourceRules sources, WorkorderMaterialService requirements,
                                          MaterialBomItemMapper components, ManufacturedInputSourceExtraMapper mapper) {
        this.sources = sources;
        this.requirements = requirements;
        this.components = components;
        this.mapper = mapper;
    }

    public record Page(List<ManufacturedInputSource> items, long total, int offset,
                       int limit) implements PageData<ManufacturedInputSource> {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Page query(Long workorderId, int offset, int limit) {
        ParameterChecks.paging(offset, limit, 100);
        if (workorderId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var order = sources.order(workorderId);
        if (!BusinessSource.LOCAL.getValue().equals(order.getSource()))
            ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var requirement = requirements.basis(order);
        var filter = new MaterialBomItemExample();
        filter.createCriteria().andBomIdEqualTo(requirement.bomId());
        if (components.countByExample(filter) != 1)
            ParameterChecks.reject(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED);
        var component = components.selectByExample(filter).getFirst();
        if (!Objects.equals(component.getMaterialId(), requirement.materialId())
                || !Objects.equals(component.getUnitId(), requirement.unitId()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        // A leaf reference does not authorize guessing a default/latest child version or outsourcing.
        if (component.getChildBomId() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        var warehouseIds = Arrays.asList(new ObjectMapper().readValue(requirement.warehouseIds(), Long[].class));
        if (warehouseIds.isEmpty() || warehouseIds.size() > 100 || warehouseIds.stream().anyMatch(Objects::isNull))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        sources.warehouses(order.getSource(), warehouseIds);
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = mapper.selectSources(component.getChildBomId(), component.getMaterialId(),
                    component.getUnitId(), warehouseIds);
            return new Page(List.copyOf(rows), page.getTotal(), offset, limit);
        }
    }
}
