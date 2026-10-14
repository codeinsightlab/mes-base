package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.dao.MesMaterialIssueErpExtraMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * External facts are owned by the MES coordinator. Call mutations only with the issue row locked.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueErpFacts {
    private final MesMaterialIssueErpExtraMapper mapper;

    public MaterialIssueErpFacts(MesMaterialIssueErpExtraMapper mapper) {
        this.mapper = mapper;
    }

    public MesMaterialIssueErpExtraEntity required(Long id) {
        var extra = mapper.selectByPrimaryKey(id);
        if (extra == null) throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        return extra;
    }

    public void initialize(MesMaterialIssueEntity row) {
        var extra = new MesMaterialIssueErpExtraEntity();
        extra.setId(row.getId());
        extra.setOperationVersion(0L);
        copy(row, extra);
        extra.setLastOperation("LOCAL_RECORD");
        extra.setLastOutcome("LOCAL_ONLY".equals(row.getSourceExecutionMode()) ? "SUCCEEDED" : "UNKNOWN");
        extra.setProvider("LOCAL_ONLY".equals(row.getSourceExecutionMode()) ? "local" : null);
        if (mapper.insert(extra) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    public MesMaterialIssueEntity hydrate(MesMaterialIssueEntity row) {
        if (row != null) project(required(row.getId()), row);
        return row;
    }

    public void hydrate(List<MesMaterialIssueEntity> rows) {
        if (rows.isEmpty()) return;
        var example = new MesMaterialIssueErpExtraExample();
        example.createCriteria().andIdIn(rows.stream().map(MesMaterialIssueEntity::getId).toList());
        Map<Long, MesMaterialIssueErpExtraEntity> values = mapper.selectByExample(example).stream()
                .collect(Collectors.toMap(MesMaterialIssueErpExtraEntity::getId, e -> e));
        for (var row : rows) {
            var e = values.get(row.getId());
            if (e == null) throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
            project(e, row);
        }
    }

    public void saveProjection(MesMaterialIssueEntity row) {
        var e = required(row.getId());
        copy(row, e);
        if (row.getVoidOperationId() != null) {
            e.setLastOperation("VOID");
            e.setLastOutcome(row.getVoidResultStatus());
            e.setLastMessage(row.getVoidMessage());
        }
        save(e);
    }

    public void save(MesMaterialIssueErpExtraEntity e) {
        e.setOperationVersion(e.getOperationVersion() + 1);
        if (mapper.updateByPrimaryKey(e) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    public void changed(Long id) {
        save(required(id));
    }

    public void delete(List<Long> ids) {
        var e = new MesMaterialIssueErpExtraExample();
        e.createCriteria().andIdIn(ids);
        if (mapper.deleteByExample(e) != ids.size()) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private void copy(MesMaterialIssueEntity r, MesMaterialIssueErpExtraEntity e) {
        e.setSourceExecutionMode(r.getSourceExecutionMode());
        e.setExternalEffect(r.getExternalEffect());
        e.setExternalBindingKey(r.getExternalBindingKey());
        e.setExternalReference(r.getExternalReference());
        e.setVoidResultStatus(r.getVoidResultStatus());
        e.setVoidExecutionMode(r.getVoidExecutionMode());
        e.setVoidMessage(r.getVoidMessage());
    }

    private void project(MesMaterialIssueErpExtraEntity e, MesMaterialIssueEntity r) {
        r.setSourceExecutionMode(e.getSourceExecutionMode());
        r.setExternalEffect(e.getExternalEffect());
        r.setExternalBindingKey(e.getExternalBindingKey());
        r.setExternalReference(e.getExternalReference());
        r.setVoidResultStatus(e.getVoidResultStatus());
        r.setVoidExecutionMode(e.getVoidExecutionMode());
        r.setVoidMessage(e.getVoidMessage());
    }
}
