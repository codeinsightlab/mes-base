package dev.mesbase.business.basic.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialCreateCommand;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.MaterialPage;
import dev.mesbase.business.basic.domain.MaterialQueryDto;
import dev.mesbase.business.basic.domain.MaterialResource;
import dev.mesbase.business.basic.domain.MaterialUpdateCommand;
import dev.mesbase.business.basic.service.IMaterialService;
import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.basic.sync.MaterialQueryPort;
import dev.mesbase.business.basic.sync.MaterialSyncResult;
import dev.mesbase.business.basic.sync.UnconfiguredMaterialQueryAdapter;
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
import dev.mesbase.business.basic.service.BasicDataRules;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * 物料产品 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialServiceImpl implements IMaterialService {
    private final MaterialMapper mapper;
    private final BasicDataRules rules;
    private final MaterialQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IMaterialService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public MaterialServiceImpl(MaterialMapper mapper, BasicDataRules rules,
                               ObjectProvider<MaterialQueryPort> queries,
                               SyncCoordinator coordinator,
                               ObjectProvider<IMaterialService> self,
                               Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.rules = rules;
        this.query = queries.getIfAvailable(UnconfiguredMaterialQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MaterialResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MaterialPage selectPage(MaterialQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MaterialExample example = new MaterialExample();
        MaterialExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.specification() != null) {
            criteria.andSpecificationLike("%" + query.specification() + "%");
        }
        if (query.categoryId() != null) {
            criteria.andCategoryIdEqualTo(query.categoryId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo(query.unitId());
        }
        if (query.materialType() != null) {
            criteria.andMaterialTypeLike("%" + query.materialType() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.externalId() != null) {
            criteria.andExternalIdLike("%" + query.externalId() + "%");
        }
        if (query.externalCode() != null) {
            criteria.andExternalCodeLike("%" + query.externalCode() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<MaterialEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MaterialEntity> rows = mapper.selectByExample(example);
            return new MaterialPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MaterialResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MaterialEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MaterialEntity insert(MaterialCreateCommand input) {
        MaterialEntity row = new MaterialEntity();

        row.setCode(input.code());
        row.setName(input.name());
        row.setSpecification(input.specification());
        row.setCategoryId(input.categoryId());
        row.setUnitId(input.unitId());
        row.setMaterialType(input.materialType());
        row.setStatus(input.status());
        row.setExternalId(input.externalId());
        row.setExternalCode(input.externalCode());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MaterialEntity updateById(Long id, MaterialUpdateCommand input) {
        MaterialEntity row = rules.lockMaterial(id);
        rules.changingUnit(row, input.unitId());
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setSpecification(input.specification());
        row.setCategoryId(input.categoryId());
        row.setUnitId(input.unitId());
        row.setMaterialType(input.materialType());
        row.setStatus(input.status());
        row.setExternalId(input.externalId());
        row.setExternalCode(input.externalCode());
        rules.validate(row);
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingMaterial(ids);
        MaterialExample example = new MaterialExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(MaterialEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(MaterialEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private MaterialEntity requireRecord(Long id) {
        MaterialEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public MaterialSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.material", Duration.ofMinutes(5));
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

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MaterialSyncResult upsert(FactoryScope scope,
                                     List<ExternalMaterial> inputs) {
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
        if (inputs.isEmpty()) return new MaterialSyncResult(0, 0, 0, 0);
        var example = new MaterialExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))

        ;
        var existing = new HashMap<String, MaterialEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        // Stabilize the full existing batch before reading identity, in primary-key order.
        for (var row : existing.values().stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList()) {
            existing.put(row.getCode(), rules.lockMaterial(row.getId()));
        }
        for (var input : inputs) {
            var row = existing.get(input.code());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (!insert) rules.changingUnit(row, input.unitId());
            if (insert) {
                row = new MaterialEntity();

            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setSpecification(input.specification());
            row.setCategoryId(input.categoryId());
            row.setUnitId(input.unitId());
            row.setMaterialType(input.materialType());
            row.setStatus(input.status());
            row.setExternalId(input.externalId());
            row.setExternalCode(input.externalCode());
            rules.validate(row);
            int affected;
            try {
                affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            } catch (DuplicateKeyException failure) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
            }
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(MaterialResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(MaterialResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new MaterialSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MaterialEntity row, ExternalMaterial input) {
        return Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getSpecification(), input.specification())
                && Objects.deepEquals(row.getCategoryId(), input.categoryId())
                && Objects.deepEquals(row.getUnitId(), input.unitId())
                && Objects.deepEquals(row.getMaterialType(), input.materialType())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getExternalId(), input.externalId())
                && Objects.deepEquals(row.getExternalCode(), input.externalCode());
    }
}

