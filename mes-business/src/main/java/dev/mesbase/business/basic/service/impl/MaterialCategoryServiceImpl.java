// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialCategoryMapper;
import dev.mesbase.business.basic.domain.MaterialCategoryCreateCommand;
import dev.mesbase.business.basic.domain.MaterialCategoryEntity;
import dev.mesbase.business.basic.domain.MaterialCategoryExample;
import dev.mesbase.business.basic.domain.MaterialCategoryPage;
import dev.mesbase.business.basic.domain.MaterialCategoryQueryDto;
import dev.mesbase.business.basic.domain.MaterialCategoryResource;
import dev.mesbase.business.basic.domain.MaterialCategoryUpdateCommand;
import dev.mesbase.business.basic.service.IMaterialCategoryService;
import dev.mesbase.business.basic.sync.ExternalMaterialCategory;
import dev.mesbase.business.basic.sync.MaterialCategoryQueryPort;
import dev.mesbase.business.basic.sync.MaterialCategorySyncResult;
import dev.mesbase.business.basic.sync.UnconfiguredMaterialCategoryQueryAdapter;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物料产品分类 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialCategoryServiceImpl implements IMaterialCategoryService {
    private final MaterialCategoryMapper mapper;
    private final MaterialCategoryQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IMaterialCategoryService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public MaterialCategoryServiceImpl(MaterialCategoryMapper mapper,
                                       ObjectProvider<MaterialCategoryQueryPort> queries,
                                       SyncCoordinator coordinator,
                                       ObjectProvider<IMaterialCategoryService> self,
                                       Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredMaterialCategoryQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MaterialCategoryResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MaterialCategoryPage selectPage(MaterialCategoryQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MaterialCategoryExample example = new MaterialCategoryExample();
        MaterialCategoryExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.parentId() != null) {
            criteria.andParentIdEqualTo(query.parentId());
        }
        if (query.sort() != null) {
            criteria.andSortEqualTo(query.sort());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id");
        try (Page<MaterialCategoryEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MaterialCategoryEntity> rows = mapper.selectByExample(example);
            return new MaterialCategoryPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MaterialCategoryResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MaterialCategoryEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialCategoryResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MaterialCategoryEntity insert(MaterialCategoryCreateCommand input) {
        MaterialCategoryEntity row = new MaterialCategoryEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setParentId(input.parentId());
        row.setSort(input.sort());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialCategoryResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MaterialCategoryEntity updateById(Long id, MaterialCategoryUpdateCommand input) {
        MaterialCategoryEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setParentId(input.parentId());
        row.setSort(input.sort());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialCategoryResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MaterialCategoryExample example = new MaterialCategoryExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MaterialCategoryEntity requireRecord(Long id) {
        MaterialCategoryEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public MaterialCategorySyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.material_category", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var result = self.getObject().upsert(scope, inputs);
            coordinator.progress(run, result.fetched());
            coordinator.succeed(run, null);
            return result;
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException deliveryFailure) {
                failure.addSuppressed(deliveryFailure);
            }
            throw failure;
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MaterialCategorySyncResult upsert(FactoryScope scope,
                                             List<ExternalMaterialCategory> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.code())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new MaterialCategorySyncResult(0, 0, 0, 0);
        var example = new MaterialCategoryExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))
                .andFactoryIdEqualTo(scope.factoryId().value())
        ;
        var existing = new HashMap<String, MaterialCategoryEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.code());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new MaterialCategoryEntity();
                row.setFactoryId(scope.factoryId().value());
            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setParentId(input.parentId());
            row.setSort(input.sort());
            row.setStatus(input.status());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(MaterialCategoryResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(MaterialCategoryResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new MaterialCategorySyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MaterialCategoryEntity row, ExternalMaterialCategory input) {
        return true
                && Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getParentId(), input.parentId())
                && Objects.deepEquals(row.getSort(), input.sort())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

