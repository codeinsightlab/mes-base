// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.sales.dao.MesSalesOrderItemMapper;
import dev.mesbase.business.sales.domain.MesSalesOrderItemCreateCommand;
import dev.mesbase.business.sales.domain.MesSalesOrderItemEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemExample;
import dev.mesbase.business.sales.domain.MesSalesOrderItemPage;
import dev.mesbase.business.sales.domain.MesSalesOrderItemQueryDto;
import dev.mesbase.business.sales.domain.MesSalesOrderItemResource;
import dev.mesbase.business.sales.domain.MesSalesOrderItemUpdateCommand;
import dev.mesbase.business.sales.service.IMesSalesOrderItemService;
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
 * 销售订单明细 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesSalesOrderItemServiceImpl implements IMesSalesOrderItemService {
    private final MesSalesOrderItemMapper mapper;

    public MesSalesOrderItemServiceImpl(MesSalesOrderItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesSalesOrderItemResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesSalesOrderItemPage selectPage(MesSalesOrderItemQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesSalesOrderItemExample example = new MesSalesOrderItemExample();
        MesSalesOrderItemExample.Criteria criteria = example.createCriteria();
        if (query.salesOrderId() != null) {
            criteria.andSalesOrderIdEqualTo( query.salesOrderId());
        }
        if (query.lineNo() != null) {
            criteria.andLineNoEqualTo( query.lineNo());
        }
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo( query.materialId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo( query.unitId());
        }
        if (query.orderedQty() != null) {
            criteria.andOrderedQtyEqualTo( query.orderedQty());
        }
        if (query.requiredDate() != null) {
            criteria.andRequiredDateEqualTo( query.requiredDate());
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
        try (Page<MesSalesOrderItemEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesSalesOrderItemEntity> rows = mapper.selectByExample(example);
            return new MesSalesOrderItemPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesSalesOrderItemResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesSalesOrderItemEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesSalesOrderItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesSalesOrderItemEntity insert(MesSalesOrderItemCreateCommand input) {
        MesSalesOrderItemEntity row = new MesSalesOrderItemEntity();
        row.setSalesOrderId(input.salesOrderId());
        row.setLineNo(input.lineNo());
        row.setMaterialId(input.materialId());
        row.setUnitId(input.unitId());
        row.setOrderedQty(input.orderedQty());
        row.setRequiredDate(input.requiredDate());
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
    @BusinessCacheEvict(business = MesSalesOrderItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesSalesOrderItemEntity updateById(Long id, MesSalesOrderItemUpdateCommand input) {
        MesSalesOrderItemEntity row = requireRecord(id);
        row.setId(id);
        row.setSalesOrderId(input.salesOrderId());
        row.setLineNo(input.lineNo());
        row.setMaterialId(input.materialId());
        row.setUnitId(input.unitId());
        row.setOrderedQty(input.orderedQty());
        row.setRequiredDate(input.requiredDate());
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
    @BusinessCacheEvict(business = MesSalesOrderItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesSalesOrderItemExample example = new MesSalesOrderItemExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesSalesOrderItemEntity requireRecord(Long id) {
        MesSalesOrderItemEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

