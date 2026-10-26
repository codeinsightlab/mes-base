// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.sales.dao.MesSalesOrderMapper;
import dev.mesbase.business.sales.domain.MesSalesOrderCreateCommand;
import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderExample;
import dev.mesbase.business.sales.domain.MesSalesOrderPage;
import dev.mesbase.business.sales.domain.MesSalesOrderQueryDto;
import dev.mesbase.business.sales.domain.MesSalesOrderResource;
import dev.mesbase.business.sales.domain.MesSalesOrderUpdateCommand;
import dev.mesbase.business.sales.service.IMesSalesOrderService;
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
 * 销售订单 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesSalesOrderServiceImpl implements IMesSalesOrderService {
    private final MesSalesOrderMapper mapper;

    public MesSalesOrderServiceImpl(MesSalesOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesSalesOrderResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesSalesOrderPage selectPage(MesSalesOrderQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesSalesOrderExample example = new MesSalesOrderExample();
        MesSalesOrderExample.Criteria criteria = example.createCriteria();
        if (query.orderNo() != null) {
            criteria.andOrderNoLike("%" + query.orderNo() + "%");
        }
        if (query.customerId() != null) {
            criteria.andCustomerIdEqualTo( query.customerId());
        }
        if (query.orderDate() != null) {
            criteria.andOrderDateEqualTo( query.orderDate());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.sourceType() != null) {
            criteria.andSourceTypeLike("%" + query.sourceType() + "%");
        }
        if (query.externalId() != null) {
            criteria.andExternalIdLike("%" + query.externalId() + "%");
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
        try (Page<MesSalesOrderEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesSalesOrderEntity> rows = mapper.selectByExample(example);
            return new MesSalesOrderPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesSalesOrderResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesSalesOrderEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesSalesOrderResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesSalesOrderEntity insert(MesSalesOrderCreateCommand input) {
        MesSalesOrderEntity row = new MesSalesOrderEntity();
        row.setOrderNo(input.orderNo());
        row.setCustomerId(input.customerId());
        row.setOrderDate(input.orderDate());
        row.setStatus(input.status());
        row.setSourceType(input.sourceType());
        row.setExternalId(input.externalId());
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
    @BusinessCacheEvict(business = MesSalesOrderResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesSalesOrderEntity updateById(Long id, MesSalesOrderUpdateCommand input) {
        MesSalesOrderEntity row = requireRecord(id);
        row.setId(id);
        row.setOrderNo(input.orderNo());
        row.setCustomerId(input.customerId());
        row.setOrderDate(input.orderDate());
        row.setStatus(input.status());
        row.setSourceType(input.sourceType());
        row.setExternalId(input.externalId());
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
    @BusinessCacheEvict(business = MesSalesOrderResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesSalesOrderExample example = new MesSalesOrderExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesSalesOrderEntity requireRecord(Long id) {
        MesSalesOrderEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

