// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.dao.MaterialCategoryMapper;
import dev.mesbase.modules.material.domain.MaterialCategoryEntity;
import dev.mesbase.modules.material.domain.MaterialCategoryExample;
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
public class MaterialCategorySyncWriter {
    private final MaterialCategoryMapper mapper;
    private final Validator validator;
    private final BasicDataRules rules;

    public MaterialCategorySyncWriter(MaterialCategoryMapper mapper, Validator validator, BasicDataRules rules) {
        this.mapper = mapper;
        this.validator = validator;
        this.rules = rules;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MaterialCategorySyncResult upsert(FactoryScope scope,
                                             List<ExternalMaterialCategory> inputs) {
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
        if (inputs.isEmpty()) return new MaterialCategorySyncResult(0, 0, 0, 0);
        var example = new MaterialCategoryExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))
                .andFactoryIdEqualTo(scope.factoryId().value())
        ;
        var existing = new HashMap<String, MaterialCategoryEntity>();
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
                row = new MaterialCategoryEntity();
                row.setFactoryId(scope.factoryId().value());
            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setParentId(input.parentId());
            row.setSort(input.sort());
            row.setStatus(input.status());
            row.setRemark(input.remark());
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
        return new MaterialCategorySyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MaterialCategoryEntity row, ExternalMaterialCategory input) {
        return true
                && Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getParentId(), input.parentId())
                && Objects.deepEquals(row.getSort(), input.sort())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

