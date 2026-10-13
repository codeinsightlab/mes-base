package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.RoutingStepMapper;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.RoutingStepExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reference locks are acquired inside the caller's business transaction through generated SQL.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class RoutingReferenceRules {
    private final OperationMapper operations;
    private final MaterialMapper materials;
    private final RoutingStepMapper steps;
    private final RoutingProductMapper products;

    public RoutingReferenceRules(OperationMapper operations, MaterialMapper materials,
                                 RoutingStepMapper steps, RoutingProductMapper products) {
        this.operations = operations;
        this.materials = materials;
        this.steps = steps;
        this.products = products;
    }

    private String factory() {
        return FactoryContext.required().factoryId().value();
    }

    public List<OperationEntity> lockOperations(List<Long> ids, boolean enabled) {
        if (ids.isEmpty()) return List.of();
        var filter = new OperationExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andIdIn(ids);
        // Same-value UPDATE takes a database row lock without a second persistence implementation.
        var lock = new OperationEntity();
        lock.setFactoryId(factory());
        operations.updateByExampleSelective(lock, filter);
        var rows = operations.selectByExample(filter);
        if (rows.size() != ids.size() || enabled && rows.stream()
                .anyMatch(row -> !"ENABLED".equals(row.getStatus()))) {
            throw new FoundationException(ErrorCode.ROUTING_REFERENCE_INVALID);
        }
        return rows;
    }

    public List<MaterialEntity> lockMaterials(List<Long> ids, boolean enabled) {
        if (ids.isEmpty()) return List.of();
        var filter = new MaterialExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andIdIn(ids);
        var lock = new MaterialEntity();
        lock.setFactoryId(factory());
        materials.updateByExampleSelective(lock, filter);
        var rows = materials.selectByExample(filter);
        if (rows.size() != ids.size() || enabled && rows.stream()
                .anyMatch(row -> !"ENABLED".equals(row.getStatus()))) {
            throw new FoundationException(ErrorCode.ROUTING_REFERENCE_INVALID);
        }
        return rows;
    }

    public void deletingOperations(List<Long> ids) {
        lockOperations(ids, false);
        var filter = new RoutingStepExample();
        filter.createCriteria().andOperationIdIn(ids);
        if (steps.countByExample(filter) > 0) {
            throw new FoundationException(ErrorCode.OPERATION_REFERENCED);
        }
    }

    public void deletingMaterials(List<Long> ids) {
        lockMaterials(ids, false);
        var filter = new RoutingProductExample();
        filter.createCriteria().andMaterialIdIn(ids);
        if (products.countByExample(filter) > 0) {
            throw new FoundationException(ErrorCode.MATERIAL_ROUTING_REFERENCED);
        }
    }
}
