// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionPlanMapper;
import dev.mesbase.business.production.domain.MesProductionPlanCreateCommand;
import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.business.production.domain.MesProductionPlanExample;
import dev.mesbase.business.production.domain.MesProductionPlanPage;
import dev.mesbase.business.production.domain.MesProductionPlanQueryDto;
import dev.mesbase.business.production.domain.MesProductionPlanResource;
import dev.mesbase.business.production.domain.MesProductionPlanUpdateCommand;
import dev.mesbase.business.production.service.IMesProductionPlanService;
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
 * 生产计划 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesProductionPlanServiceImpl implements IMesProductionPlanService {
    private final MesProductionPlanMapper mapper;

    public MesProductionPlanServiceImpl(MesProductionPlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesProductionPlanResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesProductionPlanPage selectPage(MesProductionPlanQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesProductionPlanExample example = new MesProductionPlanExample();
        MesProductionPlanExample.Criteria criteria = example.createCriteria();
        if (query.planNo() != null) {
            criteria.andPlanNoLike("%" + query.planNo() + "%");
        }
        if (query.planDate() != null) {
            criteria.andPlanDateEqualTo( query.planDate());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
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
        try (Page<MesProductionPlanEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesProductionPlanEntity> rows = mapper.selectByExample(example);
            return new MesProductionPlanPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesProductionPlanResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesProductionPlanEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesProductionPlanEntity insert(MesProductionPlanCreateCommand input) {
        MesProductionPlanEntity row = new MesProductionPlanEntity();
        row.setPlanNo(input.planNo());
        row.setPlanDate(input.planDate());
        row.setStatus(input.status());
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
    @BusinessCacheEvict(business = MesProductionPlanResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesProductionPlanEntity updateById(Long id, MesProductionPlanUpdateCommand input) {
        MesProductionPlanEntity row = requireRecord(id);
        row.setId(id);
        row.setPlanNo(input.planNo());
        row.setPlanDate(input.planDate());
        row.setStatus(input.status());
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
    @BusinessCacheEvict(business = MesProductionPlanResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesProductionPlanExample example = new MesProductionPlanExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesProductionPlanEntity requireRecord(Long id) {
        MesProductionPlanEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

