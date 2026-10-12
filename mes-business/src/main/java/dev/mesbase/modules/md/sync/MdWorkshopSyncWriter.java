// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.md.dao.MdWorkshopMapper;
import dev.mesbase.modules.md.domain.MdWorkshopEntity;
import dev.mesbase.modules.md.domain.MdWorkshopExample;
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
public class MdWorkshopSyncWriter {
    private final MdWorkshopMapper mapper;
    private final Validator validator;

    public MdWorkshopSyncWriter(MdWorkshopMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MdWorkshopSyncResult upsert(FactoryScope scope,
                                       List<ExternalMdWorkshop> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.workshopCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new MdWorkshopSyncResult(0, 0, 0, 0);
        var example = new MdWorkshopExample();
        example.createCriteria().andWorkshopCodeIn(List.copyOf(keys));
        var existing = new HashMap<String, MdWorkshopEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getWorkshopCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.workshopCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new MdWorkshopEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setWorkshopCode(input.workshopCode());
            row.setWorkshopName(input.workshopName());
            row.setArea(input.area());
            row.setCharge(input.charge());
            row.setDeptId(input.deptId());
            row.setEnableFlag(input.enableFlag());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        return new MdWorkshopSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MdWorkshopEntity row, ExternalMdWorkshop input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getWorkshopCode(), input.workshopCode())
                && Objects.deepEquals(row.getWorkshopName(), input.workshopName())
                && (row.getArea() == null ? input.area() == null
                : input.area() != null && row.getArea().compareTo(input.area()) == 0)
                && Objects.deepEquals(row.getCharge(), input.charge())
                && Objects.deepEquals(row.getDeptId(), input.deptId())
                && Objects.deepEquals(row.getEnableFlag(), input.enableFlag())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

