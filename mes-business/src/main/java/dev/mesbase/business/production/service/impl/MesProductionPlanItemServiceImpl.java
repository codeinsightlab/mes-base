// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionPlanItemMapper;
import dev.mesbase.business.production.domain.MesProductionPlanItemCreateCommand;
import dev.mesbase.business.production.domain.MesProductionPlanItemEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemExample;
import dev.mesbase.business.production.domain.MesProductionPlanItemPage;
import dev.mesbase.business.production.domain.MesProductionPlanItemQueryDto;
import dev.mesbase.business.production.domain.MesProductionPlanItemResource;
import dev.mesbase.business.production.domain.MesProductionPlanItemUpdateCommand;
import dev.mesbase.business.production.service.IMesProductionPlanItemService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产计划明细 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesProductionPlanItemServiceImpl implements IMesProductionPlanItemService {
    private final MesProductionPlanItemMapper mapper;

    public MesProductionPlanItemServiceImpl(MesProductionPlanItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesProductionPlanItemResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesProductionPlanItemPage selectPage(MesProductionPlanItemQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesProductionPlanItemExample example = new MesProductionPlanItemExample();
        MesProductionPlanItemExample.Criteria criteria = example.createCriteria();
        if (query.planId() != null) {
            criteria.andPlanIdEqualTo( query.planId());
        }
        if (query.lineNo() != null) {
            criteria.andLineNoEqualTo( query.lineNo());
        }
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo( query.materialId());
        }
        if (query.plannedQty() != null) {
            criteria.andPlannedQtyEqualTo( query.plannedQty());
        }
        if (query.plannedStartAt() != null) {
            criteria.andPlannedStartAtEqualTo( query.plannedStartAt());
        }
        if (query.plannedEndAt() != null) {
            criteria.andPlannedEndAtEqualTo( query.plannedEndAt());
        }
        if (query.priority() != null) {
            criteria.andPriorityEqualTo( query.priority());
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        if (query.revision() != null) {
            criteria.andRevisionEqualTo( query.revision());
        }
        if (query.createdBy() != null) {
            criteria.andCreatedByEqualTo( query.createdBy());
        }
        if (query.createdAt() != null) {
            criteria.andCreatedAtEqualTo( query.createdAt());
        }
        if (query.updatedBy() != null) {
            criteria.andUpdatedByEqualTo( query.updatedBy());
        }
        if (query.updatedAt() != null) {
            criteria.andUpdatedAtEqualTo( query.updatedAt());
        }
        example.setOrderByClause("id desc");
        try (Page<MesProductionPlanItemEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesProductionPlanItemEntity> rows = mapper.selectByExample(example);
            return new MesProductionPlanItemPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesProductionPlanItemResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesProductionPlanItemEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesProductionPlanItemEntity insert(MesProductionPlanItemCreateCommand input) {
        MesProductionPlanItemEntity row = new MesProductionPlanItemEntity();
        row.setPlanId(input.planId());
        row.setLineNo(input.lineNo());
        row.setMaterialId(input.materialId());
        row.setPlannedQty(input.plannedQty());
        row.setPlannedStartAt(input.plannedStartAt());
        row.setPlannedEndAt(input.plannedEndAt());
        row.setPriority(input.priority());
        row.setRemark(input.remark());
        row.setRevision(input.revision());
        row.setCreatedBy(input.createdBy());
        row.setCreatedAt(input.createdAt());
        row.setUpdatedBy(input.updatedBy());
        row.setUpdatedAt(input.updatedAt());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesProductionPlanItemEntity updateById(Long id, MesProductionPlanItemUpdateCommand input) {
        MesProductionPlanItemEntity row = requireRecord(id);
        row.setId(id);
        row.setPlanId(input.planId());
        row.setLineNo(input.lineNo());
        row.setMaterialId(input.materialId());
        row.setPlannedQty(input.plannedQty());
        row.setPlannedStartAt(input.plannedStartAt());
        row.setPlannedEndAt(input.plannedEndAt());
        row.setPriority(input.priority());
        row.setRemark(input.remark());
        row.setRevision(input.revision());
        row.setCreatedBy(input.createdBy());
        row.setCreatedAt(input.createdAt());
        row.setUpdatedBy(input.updatedBy());
        row.setUpdatedAt(input.updatedAt());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesProductionPlanItemExample example = new MesProductionPlanItemExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesProductionPlanItemEntity requireRecord(Long id) {
        MesProductionPlanItemEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

