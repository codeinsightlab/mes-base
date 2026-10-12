// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.dao.SafetyStockMapper;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockExample;
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
public class SafetyStockSyncWriter {
    private final SafetyStockMapper mapper;
    private final Validator validator;
    private final BasicDataRules rules;

    public SafetyStockSyncWriter(SafetyStockMapper mapper, Validator validator, BasicDataRules rules) {
        this.mapper = mapper;
        this.validator = validator;
        this.rules = rules;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public SafetyStockSyncResult upsert(FactoryScope scope,
                                        List<ExternalSafetyStock> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<Long>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.materialId())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new SafetyStockSyncResult(0, 0, 0, 0);
        var example = new SafetyStockExample();
        example.createCriteria().andMaterialIdIn(List.copyOf(keys))
                .andFactoryIdEqualTo(scope.factoryId().value())
        ;
        var existing = new HashMap<Long, SafetyStockEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getMaterialId(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.materialId());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new SafetyStockEntity();
                row.setFactoryId(scope.factoryId().value());
            }
            row.setMaterialId(input.materialId());
            row.setMinQty(input.minQty());
            row.setMaxQty(input.maxQty());
            row.setStatus(input.status());
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
        return new SafetyStockSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(SafetyStockEntity row, ExternalSafetyStock input) {
        return true
                && Objects.deepEquals(row.getMaterialId(), input.materialId())
                && (row.getMinQty() == null ? input.minQty() == null
                : input.minQty() != null && row.getMinQty().compareTo(input.minQty()) == 0)
                && (row.getMaxQty() == null ? input.maxQty() == null
                : input.maxQty() != null && row.getMaxQty().compareTo(input.maxQty()) == 0)
                && Objects.deepEquals(row.getStatus(), input.status())
                ;
    }
}

