package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.dao.EquipmentMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.basic.domain.EquipmentExample;
import dev.mesbase.business.basic.domain.WorkstationEntity;
import dev.mesbase.business.basic.domain.WorkstationExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Equipment assignment and workstation deletion share database row locks.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class EquipmentReferenceRules {
    private final WorkstationMapper workstations;
    private final EquipmentMapper equipment;

    public EquipmentReferenceRules(WorkstationMapper workstations, EquipmentMapper equipment) {
        this.workstations = workstations;
        this.equipment = equipment;
    }

    public void lockWorkstations(List<Long> ids) {
        var filter = new WorkstationExample();
        filter.createCriteria().andIdIn(ids);
        var filterLockedRows = new ArrayList<WorkstationEntity>();
        for (var reference : workstations.selectByExample(filter).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId())).toList()) {
            var locked = workstations.selectByPrimaryKeyForUpdate(reference.getId());
            if (locked == null) throw new FoundationException(ErrorCode.NOT_FOUND);
            filterLockedRows.add(locked);
        }
        if (filterLockedRows.size() != ids.size()) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
    }

    public void deletingWorkstations(List<Long> ids) {
        lockWorkstations(ids);
        var filter = new EquipmentExample();
        filter.createCriteria()
                .andWorkstationIdIn(ids);
        if (equipment.countByExample(filter) > 0) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }
}
