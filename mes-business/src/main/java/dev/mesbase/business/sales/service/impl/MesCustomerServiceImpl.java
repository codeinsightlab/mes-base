// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.sales.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.sales.dao.MesCustomerMapper;
import dev.mesbase.business.sales.domain.MesCustomerCreateCommand;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesCustomerExample;
import dev.mesbase.business.sales.domain.MesCustomerPage;
import dev.mesbase.business.sales.domain.MesCustomerQueryDto;
import dev.mesbase.business.sales.domain.MesCustomerResource;
import dev.mesbase.business.sales.domain.MesCustomerUpdateCommand;
import dev.mesbase.business.sales.service.IMesCustomerService;
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
 * 销售客户 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesCustomerServiceImpl implements IMesCustomerService {
    private final MesCustomerMapper mapper;

    public MesCustomerServiceImpl(MesCustomerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesCustomerResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesCustomerPage selectPage(MesCustomerQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesCustomerExample example = new MesCustomerExample();
        MesCustomerExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.shortName() != null) {
            criteria.andShortNameLike("%" + query.shortName() + "%");
        }
        if (query.contactPerson() != null) {
            criteria.andContactPersonLike("%" + query.contactPerson() + "%");
        }
        if (query.contactPhone() != null) {
            criteria.andContactPhoneLike("%" + query.contactPhone() + "%");
        }
        if (query.address() != null) {
            criteria.andAddressLike("%" + query.address() + "%");
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
        try (Page<MesCustomerEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesCustomerEntity> rows = mapper.selectByExample(example);
            return new MesCustomerPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesCustomerResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesCustomerEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesCustomerResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesCustomerEntity insert(MesCustomerCreateCommand input) {
        MesCustomerEntity row = new MesCustomerEntity();
        row.setCode(input.code());
        row.setName(input.name());
        row.setShortName(input.shortName());
        row.setContactPerson(input.contactPerson());
        row.setContactPhone(input.contactPhone());
        row.setAddress(input.address());
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
    @BusinessCacheEvict(business = MesCustomerResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesCustomerEntity updateById(Long id, MesCustomerUpdateCommand input) {
        MesCustomerEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setShortName(input.shortName());
        row.setContactPerson(input.contactPerson());
        row.setContactPhone(input.contactPhone());
        row.setAddress(input.address());
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
    @BusinessCacheEvict(business = MesCustomerResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesCustomerExample example = new MesCustomerExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesCustomerEntity requireRecord(Long id) {
        MesCustomerEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

