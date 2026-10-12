// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.qc.dao.QcIndexMapper;
import dev.mesbase.modules.qc.domain.QcIndexEntity;
import dev.mesbase.modules.qc.domain.QcIndexExample;
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
public class QcIndexSyncWriter {
    private final QcIndexMapper mapper;
    private final Validator validator;

    public QcIndexSyncWriter(QcIndexMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public QcIndexSyncResult upsert(FactoryScope scope,
                                    List<ExternalQcIndex> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.indexCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new QcIndexSyncResult(0, 0, 0, 0);
        var example = new QcIndexExample();
        example.createCriteria().andIndexCodeIn(List.copyOf(keys));
        var existing = new HashMap<String, QcIndexEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getIndexCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.indexCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new QcIndexEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setIndexCode(input.indexCode());
            row.setIndexName(input.indexName());
            row.setIndexType(input.indexType());
            row.setQcTool(input.qcTool());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        return new QcIndexSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(QcIndexEntity row, ExternalQcIndex input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getIndexCode(), input.indexCode())
                && Objects.deepEquals(row.getIndexName(), input.indexName())
                && Objects.deepEquals(row.getIndexType(), input.indexType())
                && Objects.deepEquals(row.getQcTool(), input.qcTool())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

