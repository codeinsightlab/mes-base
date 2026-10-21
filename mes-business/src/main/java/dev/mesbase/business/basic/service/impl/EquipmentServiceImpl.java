package dev.mesbase.business.basic.service.impl;

import dev.mesbase.business.shared.enums.EnableStatus;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.EquipmentMapper;
import dev.mesbase.business.basic.domain.EquipmentCreateCommand;
import dev.mesbase.business.basic.domain.EquipmentEntity;
import dev.mesbase.business.basic.domain.EquipmentExample;
import dev.mesbase.business.basic.domain.EquipmentPage;
import dev.mesbase.business.basic.domain.EquipmentQueryDto;
import dev.mesbase.business.basic.domain.EquipmentResource;
import dev.mesbase.business.basic.domain.EquipmentUpdateCommand;
import dev.mesbase.business.basic.service.IEquipmentService;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import dev.mesbase.business.basic.service.EquipmentReferenceRules;


/**
 * 设备 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class EquipmentServiceImpl implements IEquipmentService {
    private final EquipmentMapper mapper;
    private final EquipmentReferenceRules references;

    public EquipmentServiceImpl(EquipmentMapper mapper, EquipmentReferenceRules references) {
        this.mapper = mapper;
        this.references = references;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = EquipmentResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public EquipmentPage selectPage(EquipmentQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        EquipmentExample example = new EquipmentExample();
        EquipmentExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.model() != null) {
            criteria.andModelLike("%" + query.model() + "%");
        }
        if (query.workstationId() != null) {
            criteria.andWorkstationIdEqualTo(query.workstationId());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<EquipmentEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<EquipmentEntity> rows = mapper.selectByExample(example);
            return new EquipmentPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = EquipmentResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public EquipmentEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = EquipmentResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public EquipmentEntity insert(EquipmentCreateCommand input) {
        EquipmentEntity row = new EquipmentEntity();

        row.setCode(input.code());
        row.setName(input.name());
        row.setModel(input.model());
        row.setWorkstationId(input.workstationId());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        validate(row);
        int affected;
        try {
            affected = mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        if (affected != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = EquipmentResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public EquipmentEntity updateById(Long id, EquipmentUpdateCommand input) {
        EquipmentEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setModel(input.model());
        row.setWorkstationId(input.workstationId());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        validate(row);
        int affected;
        try {
            affected = mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        if (affected != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = EquipmentResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        EquipmentExample example = new EquipmentExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private void validate(EquipmentEntity row) {
        if (EnableStatus.from(row.getStatus()) == null)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var example = new EquipmentExample();
        var criteria = example.createCriteria().andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        if (mapper.countByExample(example) > 0) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        if (row.getWorkstationId() != null) references.lockWorkstations(List.of(row.getWorkstationId()));
    }

    private EquipmentEntity requireRecord(Long id) {
        EquipmentEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

