package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.domain.WorkorderErpExtraEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Preserves imported/manual external identifiers; absent provider never means verified ERP success.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkorderErpFacts {
    private final WorkorderErpExtraMapper mapper;

    public WorkorderErpFacts(WorkorderErpExtraMapper mapper) {
        this.mapper = mapper;
    }

    public void hydrate(List<WorkorderEntity> rows) {
        if (!hydrateAvailable(rows).isEmpty()) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    /**
     * List reads retain incomplete orders and report missing extensions explicitly; commands stay strict.
     */
    public Set<Long> hydrateAvailable(List<WorkorderEntity> rows) {
        if (rows.isEmpty()) return Set.of();
        var missing = new HashSet<Long>();
        var e = new WorkorderErpExtraExample();
        e.createCriteria().andIdIn(rows.stream().map(WorkorderEntity::getId).toList());
        var byId = mapper.selectByExample(e).stream().collect(Collectors.toMap(WorkorderErpExtraEntity::getId, x -> x));
        for (var row : rows) {
            var extra = byId.get(row.getId());
            if (extra == null) {
                row.setExternalId(null);
                row.setExternalCode(null);
                missing.add(row.getId());
                continue;
            }
            row.setExternalId(extra.getExternalId());
            row.setExternalCode(extra.getExternalCode());
        }
        return Set.copyOf(missing);
    }

    public void delete(Long id) {
        if (mapper.deleteByPrimaryKey(id) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    public void save(WorkorderEntity row, boolean created) {
        var extra = created ? new WorkorderErpExtraEntity() : mapper.selectByPrimaryKey(row.getId());
        if (extra == null) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        extra.setId(row.getId());
        extra.setExternalId(row.getExternalId());
        extra.setExternalCode(row.getExternalCode());
        if ((created ? mapper.insert(extra) : mapper.updateByPrimaryKey(extra)) != 1)
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
