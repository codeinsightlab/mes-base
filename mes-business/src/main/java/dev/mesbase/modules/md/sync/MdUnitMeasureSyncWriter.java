// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.md.dao.MdUnitMeasureMapper;
import dev.mesbase.modules.md.domain.MdUnitMeasureEntity;
import dev.mesbase.modules.md.domain.MdUnitMeasureExample;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All source reads occur before this independent Spring-proxied write transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdUnitMeasureSyncWriter {
    private final MdUnitMeasureMapper mapper;
    private final Validator validator;

    public MdUnitMeasureSyncWriter(MdUnitMeasureMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MdUnitMeasureSyncResult upsert(FactoryScope scope,
                                          List<ExternalMdUnitMeasure> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.measureCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new MdUnitMeasureSyncResult(0, 0, 0, 0);
        var example = new MdUnitMeasureExample();
        example.createCriteria().andMeasureCodeIn(List.copyOf(keys));
        var existing = new HashMap<String, MdUnitMeasureEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getMeasureCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.measureCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new MdUnitMeasureEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setMeasureCode(input.measureCode());
            row.setMeasureName(input.measureName());
            row.setPrimaryFlag(input.primaryFlag());
            row.setPrimaryId(input.primaryId());
            row.setChangeRate(input.changeRate());
            row.setEnableFlag(input.enableFlag());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        return new MdUnitMeasureSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MdUnitMeasureEntity row, ExternalMdUnitMeasure input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getMeasureCode(), input.measureCode())
                && Objects.deepEquals(row.getMeasureName(), input.measureName())
                && Objects.deepEquals(row.getPrimaryFlag(), input.primaryFlag())
                && Objects.deepEquals(row.getPrimaryId(), input.primaryId())
                && (row.getChangeRate() == null ? input.changeRate() == null
                : input.changeRate() != null && row.getChangeRate().compareTo(input.changeRate()) == 0)
                && Objects.deepEquals(row.getEnableFlag(), input.enableFlag())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

