// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.dv.dao.DvSubjectMapper;
import dev.mesbase.modules.dv.domain.DvSubjectEntity;
import dev.mesbase.modules.dv.domain.DvSubjectExample;
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
public class DvSubjectSyncWriter {
    private final DvSubjectMapper mapper;
    private final Validator validator;

    public DvSubjectSyncWriter(DvSubjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public DvSubjectSyncResult upsert(FactoryScope scope,
                                      List<ExternalDvSubject> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.subjectCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new DvSubjectSyncResult(0, 0, 0, 0);
        var example = new DvSubjectExample();
        example.createCriteria().andSubjectCodeIn(List.copyOf(keys));
        var existing = new HashMap<String, DvSubjectEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getSubjectCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.subjectCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new DvSubjectEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setSubjectCode(input.subjectCode());
            row.setSubjectName(input.subjectName());
            row.setSubjectType(input.subjectType());
            row.setSubjectContent(input.subjectContent());
            row.setSubjectStandard(input.subjectStandard());
            row.setEnableFlag(input.enableFlag());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        return new DvSubjectSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(DvSubjectEntity row, ExternalDvSubject input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getSubjectCode(), input.subjectCode())
                && Objects.deepEquals(row.getSubjectName(), input.subjectName())
                && Objects.deepEquals(row.getSubjectType(), input.subjectType())
                && Objects.deepEquals(row.getSubjectContent(), input.subjectContent())
                && Objects.deepEquals(row.getSubjectStandard(), input.subjectStandard())
                && Objects.deepEquals(row.getEnableFlag(), input.enableFlag())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

