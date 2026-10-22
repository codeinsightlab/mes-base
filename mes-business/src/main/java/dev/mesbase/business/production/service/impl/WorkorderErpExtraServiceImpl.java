package dev.mesbase.business.production.service.impl;

import dev.mesbase.business.production.domain.WorkorderErpExtraPage;
import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.domain.WorkorderErpExtraCreateCommand;
import dev.mesbase.business.production.domain.WorkorderErpExtraEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraExample;
import dev.mesbase.business.production.domain.WorkorderErpExtraQueryDto;
import dev.mesbase.business.production.domain.WorkorderErpExtraResource;
import dev.mesbase.business.production.domain.WorkorderErpExtraUpdateCommand;
import dev.mesbase.business.production.service.IWorkorderErpExtraService;
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
 * 工单外部身份 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkorderErpExtraServiceImpl implements IWorkorderErpExtraService {
    private final WorkorderErpExtraMapper mapper;

    public WorkorderErpExtraServiceImpl(WorkorderErpExtraMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WorkorderErpExtraResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public WorkorderErpExtraPage selectPage(WorkorderErpExtraQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        WorkorderErpExtraExample example = new WorkorderErpExtraExample();
        WorkorderErpExtraExample.Criteria criteria = example.createCriteria();
        if (query.externalId() != null) {
            criteria.andExternalIdLike("%" + query.externalId() + "%");
        }
        if (query.externalCode() != null) {
            criteria.andExternalCodeLike("%" + query.externalCode() + "%");
        }
        if (query.provider() != null) {
            criteria.andProviderLike("%" + query.provider() + "%");
        }
        example.setOrderByClause("id");
        try (Page<WorkorderErpExtraEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<WorkorderErpExtraEntity> rows = mapper.selectByExample(example);
            return new WorkorderErpExtraPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WorkorderErpExtraResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public WorkorderErpExtraEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkorderErpExtraResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public WorkorderErpExtraEntity insert(WorkorderErpExtraCreateCommand input) {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkorderErpExtraResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WorkorderErpExtraEntity updateById(Long id, WorkorderErpExtraUpdateCommand input) {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkorderErpExtraResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private WorkorderErpExtraEntity requireRecord(Long id) {
        WorkorderErpExtraEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

