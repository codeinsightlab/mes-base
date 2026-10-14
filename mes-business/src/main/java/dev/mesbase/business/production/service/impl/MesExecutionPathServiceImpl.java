package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.domain.MesExecutionPathCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathExample;
import dev.mesbase.business.production.domain.MesExecutionPathPage;
import dev.mesbase.business.production.domain.MesExecutionPathQueryDto;
import dev.mesbase.business.production.domain.MesExecutionPathResource;
import dev.mesbase.business.production.domain.MesExecutionPathUpdateCommand;
import dev.mesbase.business.production.service.IMesExecutionPathService;
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
 * 生产执行当前完整路径 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesExecutionPathServiceImpl implements IMesExecutionPathService {
    private final MesExecutionPathMapper mapper;

    public MesExecutionPathServiceImpl(MesExecutionPathMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesExecutionPathResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesExecutionPathPage selectPage(MesExecutionPathQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesExecutionPathExample example = new MesExecutionPathExample();
        MesExecutionPathExample.Criteria criteria = example.createCriteria();
        if (query.routingId() != null) {
            criteria.andRoutingIdEqualTo( query.routingId());
        }
        if (query.pathStatus() != null) {
            criteria.andPathStatusLike("%" + query.pathStatus() + "%");
        }
        if (query.revision() != null) {
            criteria.andRevisionEqualTo( query.revision());
        }
        if (query.selectedAt() != null) {
            criteria.andSelectedAtEqualTo( query.selectedAt());
        }
        if (query.lockedAt() != null) {
            criteria.andLockedAtEqualTo( query.lockedAt());
        }
        example.setOrderByClause("id");
        try (Page<MesExecutionPathEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesExecutionPathEntity> rows = mapper.selectByExample(example);
            return new MesExecutionPathPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesExecutionPathResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesExecutionPathEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesExecutionPathResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesExecutionPathEntity insert(MesExecutionPathCreateCommand input) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesExecutionPathResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesExecutionPathEntity updateById(Long id, MesExecutionPathUpdateCommand input) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesExecutionPathResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    private MesExecutionPathEntity requireRecord(Long id) {
        MesExecutionPathEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

