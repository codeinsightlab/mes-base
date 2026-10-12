// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict.Scope;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.modules.material.dao.SafetyStockMapper;
import dev.mesbase.modules.material.domain.SafetyStockCreateCommand;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockExample;
import dev.mesbase.modules.material.domain.SafetyStockPage;
import dev.mesbase.modules.material.domain.SafetyStockQueryDto;
import dev.mesbase.modules.material.domain.SafetyStockResource;
import dev.mesbase.modules.material.domain.SafetyStockUpdateCommand;
import dev.mesbase.modules.material.service.BasicDataRules;
import dev.mesbase.modules.material.service.ISafetyStockService;
import dev.mesbase.modules.material.sync.ExternalSafetyStock;
import dev.mesbase.modules.material.sync.SafetyStockQueryPort;
import dev.mesbase.modules.material.sync.SafetyStockSyncResult;
import dev.mesbase.modules.material.sync.UnconfiguredSafetyStockQueryAdapter;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 安全库存配置 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class SafetyStockServiceImpl implements ISafetyStockService {
    private final SafetyStockMapper mapper;
    private final BasicDataRules rules;

    private final SafetyStockQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<ISafetyStockService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public SafetyStockServiceImpl(SafetyStockMapper mapper, BasicDataRules rules
            , ObjectProvider<SafetyStockQueryPort> queries, SyncCoordinator coordinator,
                                  ObjectProvider<ISafetyStockService> self, Validator validator,
                                  BusinessCacheProcessor caches
    ) {
        this.mapper = mapper;
        this.rules = rules;
        this.query = queries.getIfAvailable(UnconfiguredSafetyStockQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = SafetyStockResource.PREFIX, type = BusinessCache.Type.LIST)
    public SafetyStockPage selectPage(SafetyStockQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        SafetyStockExample example = new SafetyStockExample();
        SafetyStockExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.minQty() != null) {
            criteria.andMinQtyEqualTo(query.minQty());
        }
        if (query.maxQty() != null) {
            criteria.andMaxQtyEqualTo(query.maxQty());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        example.setOrderByClause("id");
        try (Page<SafetyStockEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<SafetyStockEntity> rows = mapper.selectByExample(example);
            return new SafetyStockPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = SafetyStockResource.PREFIX, type = BusinessCache.Type.DETAIL)
    public SafetyStockEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = SafetyStockResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST)
    public SafetyStockEntity insert(SafetyStockCreateCommand input) {
        SafetyStockEntity row = new SafetyStockEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setMaterialId(input.materialId());
        row.setMinQty(input.minQty());
        row.setMaxQty(input.maxQty());
        row.setStatus(input.status());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = SafetyStockResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public SafetyStockEntity updateById(Long id, SafetyStockUpdateCommand input) {
        SafetyStockEntity row = requireRecord(id);
        row.setId(id);
        row.setMaterialId(input.materialId());
        row.setMinQty(input.minQty());
        row.setMaxQty(input.maxQty());
        row.setStatus(input.status());
        rules.validate(row);
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = SafetyStockResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingSafetyStock(ids);
        SafetyStockExample example = new SafetyStockExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(SafetyStockEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(SafetyStockEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private SafetyStockEntity requireRecord(Long id) {
        SafetyStockEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public SafetyStockSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.safety_stock", Duration.ofMinutes(5));
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
    public SafetyStockSyncResult upsert(FactoryScope scope,
                                        List<ExternalSafetyStock> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<Long>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.materialId())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new SafetyStockSyncResult(0, 0, 0, 0);
        var example = new SafetyStockExample();
        example.createCriteria().andMaterialIdIn(List.copyOf(keys))
                .andFactoryIdEqualTo(scope.factoryId().value())
        ;
        var existing = new HashMap<Long, SafetyStockEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getMaterialId(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.materialId());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new SafetyStockEntity();
                row.setFactoryId(scope.factoryId().value());
            }
            row.setMaterialId(input.materialId());
            row.setMinQty(input.minQty());
            row.setMaxQty(input.maxQty());
            row.setStatus(input.status());
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
        if (updated > 0) caches.evict(SafetyStockResource.PREFIX, Scope.LIST_AND_DETAIL);
        else if (created > 0) caches.evict(SafetyStockResource.PREFIX, Scope.LIST);
        return new SafetyStockSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(SafetyStockEntity row, ExternalSafetyStock input) {
        return true
                && Objects.deepEquals(row.getMaterialId(), input.materialId())
                && (row.getMinQty() == null ? input.minQty() == null
                : input.minQty() != null && row.getMinQty().compareTo(input.minQty()) == 0)
                && (row.getMaxQty() == null ? input.maxQty() == null
                : input.maxQty() != null && row.getMaxQty().compareTo(input.maxQty()) == 0)
                && Objects.deepEquals(row.getStatus(), input.status())
                ;
    }

}
