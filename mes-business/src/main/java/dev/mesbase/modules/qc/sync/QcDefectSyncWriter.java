// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.qc.dao.QcDefectMapper;
import dev.mesbase.modules.qc.domain.QcDefectEntity;
import dev.mesbase.modules.qc.domain.QcDefectExample;
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
public class QcDefectSyncWriter {
    private final QcDefectMapper mapper;
    private final Validator validator;

    public QcDefectSyncWriter(QcDefectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public QcDefectSyncResult upsert(FactoryScope scope,
                                     List<ExternalQcDefect> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.defectCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new QcDefectSyncResult(0, 0, 0, 0);
        var example = new QcDefectExample();
        example.createCriteria().andDefectCodeIn(List.copyOf(keys));
        var existing = new HashMap<String, QcDefectEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getDefectCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.defectCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new QcDefectEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setDefectCode(input.defectCode());
            row.setDefectName(input.defectName());
            row.setIndexType(input.indexType());
            row.setDefectLevel(input.defectLevel());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        return new QcDefectSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(QcDefectEntity row, ExternalQcDefect input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getDefectCode(), input.defectCode())
                && Objects.deepEquals(row.getDefectName(), input.defectName())
                && Objects.deepEquals(row.getIndexType(), input.indexType())
                && Objects.deepEquals(row.getDefectLevel(), input.defectLevel())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

