package dev.mesbase.business.quality.service;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.common.core.page.PageData;
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
import dev.mesbase.core.error.SafeBusinessMessage;
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


    public record Page(List<OperationDefectTypeEntity> items, long total, int offset,
                       int limit) implements PageData<OperationDefectTypeEntity> {
    }

    public Page page(Long operation, boolean enabledOnly, int offset, int limit) {
        if (operation == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请指定工序"));
        ParameterChecks.paging(offset, limit, 100);
        if (operations.selectByPrimaryKey(operation) == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var e = new OperationDefectTypeExample();
        var c = e.createCriteria().andOperationIdEqualTo(operation);
        if (enabledOnly) c.andStatusEqualTo(EnableStatus.ENABLED.getValue());
        e.setOrderByClause("sort ASC, id ASC");
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(mapper.selectByExample(e));
            return new Page(rows, page.getTotal(), offset, limit);
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
        if (WasteCategory.from(category) == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请选择有效的工废/料废类别"));
        return row;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public OperationDefectTypeEntity save(Long operation, Long id, Input input) {
        references.lockOperations(List.of(operation), id == null);
        if (input == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("不良类型信息不能为空"));
        ParameterChecks.text(input.code(), true, 64, ErrorCode.BAD_REQUEST,
                new SafeBusinessMessage("不良类型编码不能为空且不能超过64个字符"));
        ParameterChecks.text(input.name(), true, 255, ErrorCode.BAD_REQUEST,
                new SafeBusinessMessage("不良类型名称不能为空且不能超过255个字符"));
        if (WasteCategory.from(input.defaultWasteCategory()) == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请选择有效的默认工废/料废类别"));
        if (EnableStatus.from(input.status()) == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请选择有效的启用状态"));
        if (input.sort() == null || input.sort() < 0)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("排序值不能为负数"));
        ParameterChecks.text(input.remark(), false, 500, ErrorCode.BAD_REQUEST,
                new SafeBusinessMessage("备注不能超过500个字符"));
        String code = input.code().strip().toUpperCase(Locale.ROOT);
        var duplicate = new OperationDefectTypeExample();
        var criteria = duplicate.createCriteria().andOperationIdEqualTo(operation).andCodeEqualTo(code);
        if (id != null) criteria.andIdNotEqualTo(id);
        if (mapper.countByExample(duplicate) != 0) throw new FoundationException(ErrorCode.DEFECT_TYPE_DUPLICATE);
        var row = id == null ? new OperationDefectTypeEntity() : require(operation, id);
        if (id != null && !row.getRevision().equals(input.expectedRevision()))
            throw new FoundationException(ErrorCode.DEFECT_TYPE_STALE);
        if (id == null && input.expectedRevision() != null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("新建不良类型不能携带版本号"));
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
