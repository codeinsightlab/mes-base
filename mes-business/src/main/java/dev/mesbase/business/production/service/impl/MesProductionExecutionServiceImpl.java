package dev.mesbase.business.production.service.impl;

import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionQueryDto;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.business.production.domain.MesProductionExecutionUpdateCommand;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import org.springframework.transaction.annotation.Isolation;
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
 * 生产执行单元 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesProductionExecutionServiceImpl implements IMesProductionExecutionService {
    private final MesProductionExecutionMapper mapper;
    private final ProductionExecutionRules rules;

    public MesProductionExecutionServiceImpl(MesProductionExecutionMapper mapper, ProductionExecutionRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesProductionExecutionResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public Page<MesProductionExecutionEntity> selectPage(MesProductionExecutionQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesProductionExecutionExample example = new MesProductionExecutionExample();
        MesProductionExecutionExample.Criteria criteria = example.createCriteria();
        if (query.executionNo() != null) {
            criteria.andExecutionNoLike("%" + query.executionNo() + "%");
        }
        if (query.workorderId() != null) {
            criteria.andWorkorderIdEqualTo(query.workorderId());
        }
        if (query.productId() != null) {
            criteria.andProductIdEqualTo(query.productId());
        }
        if (query.routingId() != null) {
            criteria.andRoutingIdEqualTo(query.routingId());
        }
        if (query.targetQty() != null) {
            criteria.andTargetQtyEqualTo(query.targetQty());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.startTime() != null) {
            criteria.andStartTimeEqualTo(query.startTime());
        }
        if (query.endTime() != null) {
            criteria.andEndTimeEqualTo(query.endTime());
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<MesProductionExecutionEntity> page = PageHelper.offsetPage(offset, limit)) {
            mapper.selectByExample(example);
            return page;
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesProductionExecutionResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesProductionExecutionEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesProductionExecutionEntity insert(MesProductionExecutionCreateCommand input) {
        MesProductionExecutionEntity row = new MesProductionExecutionEntity();
        row.setWorkorderId(input.workorderId());
        row.setRoutingId(input.routingId());
        row.setTargetQty(input.targetQty());
        row.setStatus(input.status());
        row.setStartTime(input.startTime());
        row.setEndTime(input.endTime());
        row.setRemark(input.remark());
        rules.initialize(row);
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        // The temporary number is unobservable outside this transaction. Final number is PK-derived.
        row.setExecutionNo("EXE-" + row.getId());
        if (mapper.updateByPrimaryKey(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesProductionExecutionEntity updateById(Long id, MesProductionExecutionUpdateCommand input) {
        MesProductionExecutionEntity previous = rules.lock(id);
        MesProductionExecutionEntity row = new MesProductionExecutionEntity();
        row.setId(id);
        row.setExecutionNo(previous.getExecutionNo());
        row.setProductId(previous.getProductId());
        row.setCreateBy(previous.getCreateBy());
        row.setCreateTime(previous.getCreateTime());
        row.setWorkorderId(input.workorderId());
        row.setRoutingId(input.routingId());
        row.setTargetQty(input.targetQty());
        row.setStatus(input.status());
        row.setStartTime(input.startTime());
        row.setEndTime(input.endTime());
        row.setRemark(input.remark());
        rules.updating(previous, row);
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesProductionExecutionExample example = new MesProductionExecutionExample();
        for (var id : ids.stream().sorted().toList()) rules.deleting(id);
        example.createCriteria().andIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesProductionExecutionEntity requireRecord(Long id) {
        MesProductionExecutionEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

