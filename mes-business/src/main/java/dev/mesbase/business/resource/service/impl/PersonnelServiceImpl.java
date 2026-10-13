package dev.mesbase.business.resource.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.resource.dao.PersonnelMapper;
import dev.mesbase.business.resource.domain.PersonnelCreateCommand;
import dev.mesbase.business.resource.domain.PersonnelEntity;
import dev.mesbase.business.resource.domain.PersonnelExample;
import dev.mesbase.business.resource.domain.PersonnelPage;
import dev.mesbase.business.resource.domain.PersonnelQueryDto;
import dev.mesbase.business.resource.domain.PersonnelResource;
import dev.mesbase.business.resource.domain.PersonnelUpdateCommand;
import dev.mesbase.business.resource.service.IPersonnelService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.mapper.SysUserMapper;


/**
 * 生产人员 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PersonnelServiceImpl implements IPersonnelService {
    private final PersonnelMapper mapper;
    private final PlatformPersistence platform;

    public PersonnelServiceImpl(PersonnelMapper mapper, PlatformPersistence platform) {
        this.mapper = mapper;
        this.platform = platform;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = PersonnelResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public PersonnelPage selectPage(PersonnelQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        PersonnelExample example = new PersonnelExample();
        PersonnelExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.userId() != null) {
            criteria.andUserIdEqualTo(query.userId());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        example.setOrderByClause("id");
        try (Page<PersonnelEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<PersonnelEntity> rows = mapper.selectByExample(example);
            return new PersonnelPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = PersonnelResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public PersonnelEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = PersonnelResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public PersonnelEntity insert(PersonnelCreateCommand input) {
        PersonnelEntity row = new PersonnelEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setUserId(input.userId());
        row.setStatus(input.status());
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
    @BusinessCacheEvict(business = PersonnelResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public PersonnelEntity updateById(Long id, PersonnelUpdateCommand input) {
        PersonnelEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setUserId(input.userId());
        row.setStatus(input.status());
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
    @BusinessCacheEvict(business = PersonnelResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        PersonnelExample example = new PersonnelExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private void validate(PersonnelEntity row) {
        if (!Set.of("ENABLED", "DISABLED").contains(row.getStatus()))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var example = new PersonnelExample();
        var criteria = example.createCriteria().andFactoryIdEqualTo(row.getFactoryId()).andCodeEqualTo(row.getCode());
        if (row.getId() != null) criteria.andIdNotEqualTo(row.getId());
        if (mapper.countByExample(example) > 0) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        if (row.getUserId() != null) {
            boolean valid = platform.readOnly(session -> {
                var user = session.mapper(SysUserMapper.class).selectUserById(row.getUserId());
                return user != null && "0".equals(user.getDelFlag()) && "0".equals(user.getStatus());
            });
            if (!valid) throw new FoundationException(ErrorCode.NOT_FOUND);
        }
    }

    private PersonnelEntity requireRecord(Long id) {
        PersonnelEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

