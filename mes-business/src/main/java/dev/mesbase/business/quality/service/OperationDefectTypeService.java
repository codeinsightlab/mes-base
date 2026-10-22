package dev.mesbase.business.quality.service;

import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.quality.dao.OperationDefectTypeMapper;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.OperationDefectTypeEntity;
import dev.mesbase.business.quality.domain.OperationDefectTypeExample;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import dev.mesbase.business.quality.domain.WasteCategory;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class OperationDefectTypeService {
    private final OperationMapper operations;
    private final OperationDefectTypeMapper mapper;
    private final MesProductionDefectMapper defects;
    private final RoutingReferenceRules references;

    public OperationDefectTypeService(OperationDefectTypeMapper mapper, MesProductionDefectMapper defects,
                                      RoutingReferenceRules references, OperationMapper operations) {
        this.operations = operations;
        this.mapper = mapper;
        this.defects = defects;
        this.references = references;
    }

    public record Input(String code, String name, String defaultWasteCategory, String status, Integer sort,
                        String remark, Integer expectedRevision) {
    }

    public record Page(List<OperationDefectTypeEntity> items, long total, int offset, int limit) {
    }

    public Page page(Long operation, boolean enabledOnly, int offset, int limit) {
        if (operation == null || offset < 0 || limit < 1 || limit > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (operations.selectByPrimaryKey(operation) == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var e = new OperationDefectTypeExample();
        var c = e.createCriteria().andOperationIdEqualTo(operation);
        if (enabledOnly) c.andStatusEqualTo(EnableStatus.ENABLED.getValue());
        e.setOrderByClause("id desc");
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = mapper.selectByExample(e);
            return new Page(List.copyOf(rows), page.getTotal(), offset, limit);
        }
    }

    public OperationDefectTypeEntity require(Long operation, Long id) {
        var row = mapper.selectByPrimaryKey(id);
        if (row == null || !row.getOperationId().equals(operation)) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    /**
     * Shared parent lock serializes create/update/delete against declarations and operation deletion.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public OperationDefectTypeEntity lockForDeclaration(Long operation, Long id, int revision, String category) {
        references.lockOperations(List.of(operation), true);
        var row = require(operation, id);
        if (row.getRevision() != revision) throw new FoundationException(ErrorCode.DEFECT_TYPE_STALE);
        if (!EnableStatus.ENABLED.getValue().equals(row.getStatus()))
            throw new FoundationException(ErrorCode.DEFECT_TYPE_INVALID);
        if (WasteCategory.from(category) == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return row;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public OperationDefectTypeEntity save(Long operation, Long id, Input input) {
        references.lockOperations(List.of(operation), id == null);
        if (input == null || input.code() == null || input.code().isBlank() || input.code().length() > 64
                || input.name() == null || input.name().isBlank() || input.name().length() > 255
                || WasteCategory.from(input.defaultWasteCategory()) == null || EnableStatus.from(input.status()) == null
                || input.sort() == null || input.sort() < 0 || input.remark() != null && input.remark().length() > 500)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        String code = input.code().strip().toUpperCase(Locale.ROOT);
        var duplicate = new OperationDefectTypeExample();
        var criteria = duplicate.createCriteria().andOperationIdEqualTo(operation).andCodeEqualTo(code);
        if (id != null) criteria.andIdNotEqualTo(id);
        if (mapper.countByExample(duplicate) != 0) throw new FoundationException(ErrorCode.DEFECT_TYPE_DUPLICATE);
        var row = id == null ? new OperationDefectTypeEntity() : require(operation, id);
        if (id != null && !row.getRevision().equals(input.expectedRevision()))
            throw new FoundationException(ErrorCode.DEFECT_TYPE_STALE);
        if (id == null && input.expectedRevision() != null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        row.setOperationId(operation);
        row.setCode(code);
        row.setName(input.name().strip());
        row.setDefaultWasteCategory(input.defaultWasteCategory());
        row.setStatus(input.status());
        row.setSort(input.sort());
        row.setRemark(input.remark());
        row.setUpdateBy(SecurityUtils.getUserId());
        row.setUpdateTime(LocalDateTime.now());
        if (id == null) {
            row.setRevision(0);
            row.setCreateBy(row.getUpdateBy());
            row.setCreateTime(row.getUpdateTime());
            if (mapper.insert(row) != 1) ParameterChecks.reject(ErrorCode.DEFECT_TYPE_STALE);
        } else {
            var expected = new OperationDefectTypeExample();
            expected.createCriteria().andIdEqualTo(id).andOperationIdEqualTo(operation)
                    .andRevisionEqualTo(input.expectedRevision());
            row.setRevision(row.getRevision() + 1);
            if (mapper.updateByExample(row, expected) != 1) ParameterChecks.reject(ErrorCode.DEFECT_TYPE_STALE);
        }
        return require(operation, row.getId());
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void delete(Long operation, Long id, int expectedRevision) {
        references.lockOperations(List.of(operation), false);
        var row = require(operation, id);
        if (row.getRevision() != expectedRevision) ParameterChecks.reject(ErrorCode.DEFECT_TYPE_STALE);
        var used = new MesProductionDefectExample();
        used.createCriteria().andDefectTypeIdEqualTo(id);
        if (defects.countByExample(used) > 0) throw new FoundationException(ErrorCode.DEFECT_TYPE_REFERENCED);
        var e = new OperationDefectTypeExample();
        e.createCriteria().andIdEqualTo(id).andOperationIdEqualTo(operation).andRevisionEqualTo(expectedRevision);
        if (mapper.deleteByExample(e) != 1) ParameterChecks.reject(ErrorCode.DEFECT_TYPE_STALE);
    }


}
