package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.domain.MesExecutionPathNodeCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import dev.mesbase.business.production.domain.MesExecutionPathNodePage;
import dev.mesbase.business.production.domain.MesExecutionPathNodeQueryDto;
import dev.mesbase.business.production.domain.MesExecutionPathNodeResource;
import dev.mesbase.business.production.domain.MesExecutionPathNodeUpdateCommand;
import dev.mesbase.business.production.service.IMesExecutionPathNodeService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产执行选定路径的有序节点引用 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesExecutionPathNodeServiceImpl implements IMesExecutionPathNodeService {
    private final MesExecutionPathNodeMapper mapper;

    public MesExecutionPathNodeServiceImpl(MesExecutionPathNodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesExecutionPathNodeResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesExecutionPathNodePage selectPage(MesExecutionPathNodeQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesExecutionPathNodeExample example = new MesExecutionPathNodeExample();
        MesExecutionPathNodeExample.Criteria criteria = example.createCriteria();
        if (query.executionId() != null) {
            criteria.andExecutionIdEqualTo(query.executionId());
        }
        if (query.sequenceNo() != null) {
            criteria.andSequenceNoEqualTo(query.sequenceNo());
        }
        if (query.routingNodeId() != null) {
            criteria.andRoutingNodeIdEqualTo(query.routingNodeId());
        }
        example.setOrderByClause("id desc");
        try (Page<MesExecutionPathNodeEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesExecutionPathNodeEntity> rows = mapper.selectByExample(example);
            return new MesExecutionPathNodePage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesExecutionPathNodeResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesExecutionPathNodeEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesExecutionPathNodeResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesExecutionPathNodeEntity insert(MesExecutionPathNodeCreateCommand input) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesExecutionPathNodeResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesExecutionPathNodeEntity updateById(Long id, MesExecutionPathNodeUpdateCommand input) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesExecutionPathNodeResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    private MesExecutionPathNodeEntity requireRecord(Long id) {
        MesExecutionPathNodeEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

