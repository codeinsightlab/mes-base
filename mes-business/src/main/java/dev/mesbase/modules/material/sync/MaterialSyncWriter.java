// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.sync;

import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict.Scope;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.dao.MaterialMapper;
import dev.mesbase.modules.material.domain.MaterialEntity;
import dev.mesbase.modules.material.domain.MaterialResource;
import dev.mesbase.modules.material.domain.MaterialExample;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import dev.mesbase.modules.material.service.BasicDataRules;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * All source reads occur before this independent Spring-proxied write transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialSyncWriter {
    private final MaterialMapper mapper;
    private final Validator validator;
    private final BasicDataRules rules;
    private final BusinessCacheProcessor caches;

    public MaterialSyncWriter(MaterialMapper mapper, Validator validator, BasicDataRules rules,
                              BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.validator = validator;
        this.rules = rules;
        this.caches = caches;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MaterialSyncResult upsert(FactoryScope scope,
                                     List<ExternalMaterial> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.code())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new MaterialSyncResult(0, 0, 0, 0);
        var example = new MaterialExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))
                .andFactoryIdEqualTo(scope.factoryId().value())
        ;
        var existing = new HashMap<String, MaterialEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.code());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new MaterialEntity();
                row.setFactoryId(scope.factoryId().value());
            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setSpecification(input.specification());
            row.setCategoryId(input.categoryCode() == null ? null : rules.categoryId(input.categoryCode()));
            row.setUnitId(rules.unitId(input.unitCode()));
            row.setMaterialType(input.materialType());
            row.setStatus(input.status());
            row.setExternalId(input.externalId());
            row.setExternalCode(input.externalCode());
            rules.validate(row);
            int affected;
            try {
                affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            } catch (DuplicateKeyException failure) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
            }
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) caches.evict(MaterialResource.PREFIX, Scope.LIST_AND_DETAIL);
        else if (created > 0) caches.evict(MaterialResource.PREFIX, Scope.LIST);
        return new MaterialSyncResult(inputs.size(), created, updated, skipped);
    }

    private boolean same(MaterialEntity row, ExternalMaterial input) {
        return true
                && Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getSpecification(), input.specification())
                && Objects.deepEquals(row.getCategoryId(), (input.categoryCode() == null ? null : rules.categoryId(input.categoryCode())))
                && Objects.deepEquals(row.getUnitId(), rules.unitId(input.unitCode()))
                && Objects.deepEquals(row.getMaterialType(), input.materialType())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getExternalId(), input.externalId())
                && Objects.deepEquals(row.getExternalCode(), input.externalCode())
                ;
    }
}

