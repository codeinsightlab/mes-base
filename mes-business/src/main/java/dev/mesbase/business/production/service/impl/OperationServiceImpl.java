package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.domain.OperationCreateCommand;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.OperationPage;
import dev.mesbase.business.production.domain.OperationQueryDto;
import dev.mesbase.business.production.domain.OperationResource;
import dev.mesbase.business.production.domain.OperationUpdateCommand;
import dev.mesbase.business.production.service.IOperationService;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.sync.ExternalOperation;
import dev.mesbase.business.production.sync.OperationQueryPort;
import dev.mesbase.business.production.sync.UnconfiguredOperationQueryAdapter;
import dev.mesbase.business.production.sync.ProductionSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import java.util.HashMap;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * 工序 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperationServiceImpl implements IOperationService {
    private final OperationMapper mapper;
    private final RoutingReferenceRules references;
    private final OperationQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IOperationService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public OperationServiceImpl(OperationMapper mapper, RoutingReferenceRules references,
                                ObjectProvider<OperationQueryPort> queries, SyncCoordinator coordinator,
                                ObjectProvider<IOperationService> self, Validator validator,
                                BusinessCacheProcessor caches) {
        this.references = references;
        this.query = queries.getIfAvailable(UnconfiguredOperationQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = OperationResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public OperationPage selectPage(OperationQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        OperationExample example = new OperationExample();
        OperationExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.description() != null) {
            criteria.andDescriptionLike("%" + query.description() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.sort() != null) {
            criteria.andSortEqualTo(query.sort());
        }
        if (query.externalId() != null) {
            criteria.andExternalIdLike("%" + query.externalId() + "%");
        }
        if (query.revision() != null) {
            criteria.andRevisionEqualTo(query.revision());
        }
        example.setOrderByClause("sort, id");
        try (Page<OperationEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<OperationEntity> rows = mapper.selectByExample(example);
            return new OperationPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = OperationResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public OperationEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = OperationResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public OperationEntity insert(OperationCreateCommand input) {
        OperationEntity row = new OperationEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setDescription(input.description());
        row.setStatus(input.status());
        row.setSort(input.sort());
        row.setExternalId(input.externalId());
        row.setRevision(0);
        validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = OperationResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public OperationEntity updateById(Long id, OperationUpdateCommand input) {
        OperationEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setDescription(input.description());
        row.setStatus(input.status());
        row.setSort(input.sort());
        row.setExternalId(input.externalId());
        if (!Objects.equals(row.getRevision(), input.revision())) {
            throw new FoundationException(ErrorCode.ROUTING_REVISION_CONFLICT);
        }
        row.setRevision(input.revision() + 1);
        validate(row);
        var expected = new OperationExample();
        expected.createCriteria().andIdEqualTo(id).andFactoryIdEqualTo(row.getFactoryId())
                .andRevisionEqualTo(input.revision());
        if (updateUnique(row, expected) != 1) {
            throw new FoundationException(ErrorCode.ROUTING_REVISION_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = OperationResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        OperationExample example = new OperationExample();
        for (var id : ids) requireRecord(id);
        references.deletingOperations(ids);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private void validate(OperationEntity row) {
        if (!Set.of("ENABLED", "DISABLED").contains(row.getStatus()) || row.getSort() == null
                || row.getSort() < 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
        row.setCode(row.getCode().strip());
        row.setName(row.getName().strip());
    }

    private int insertUnique(OperationEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(OperationEntity row, OperationExample filter) {
        try {
            return mapper.updateByExample(row, filter);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    public ProductionSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "operation.erp", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var result = self.getObject().upsert(scope, inputs);
            coordinator.progress(run, result.fetched());
            coordinator.succeed(run, null);
            return result;
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException delivery) {
                failure.addSuppressed(delivery);
            }
            throw failure;
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ProductionSyncResult upsert(FactoryScope scope, List<ExternalOperation> inputs) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        if (inputs == null || inputs.size() > 1000) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var keys = new HashSet<String>();
        var normalized = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty()
                    || !Set.of("ENABLED", "DISABLED").contains(input.status())
                    || !normalized.add(input.code().strip().toLowerCase(Locale.ROOT))) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
            keys.add(input.code().strip());
        }
        if (inputs.isEmpty()) return new ProductionSyncResult(0, 0, 0, 0);
        var filter = new OperationExample();
        filter.createCriteria().andFactoryIdEqualTo(scope.factoryId().value()).andCodeIn(List.copyOf(keys));
        // Lock existing definitions before reading the revision used by this write.
        var lock = new OperationEntity();
        lock.setFactoryId(scope.factoryId().value());
        mapper.updateByExampleSelective(lock, filter);
        var existing = new HashMap<String, OperationEntity>();
        for (var row : mapper.selectByExample(filter)) existing.put(row.getCode().toLowerCase(Locale.ROOT), row);
        int created = 0, updated = 0, skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.code().strip().toLowerCase(Locale.ROOT));
            if (row != null && Objects.equals(row.getName(), input.name().strip())
                    && Objects.equals(row.getDescription(), input.description())
                    && Objects.equals(row.getStatus(), input.status())
                    && Objects.equals(row.getExternalId(), input.externalId())) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new OperationEntity();
                row.setFactoryId(scope.factoryId().value());
                row.setSort(0);
                row.setRevision(0);
            } else row.setRevision(row.getRevision() + 1);
            row.setCode(input.code().strip());
            row.setName(input.name().strip());
            row.setDescription(input.description());
            row.setStatus(input.status());
            row.setExternalId(input.externalId());
            validate(row);
            int count = insert ? insertUnique(row) : updateUnique(row, filterById(row));
            if (count != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (created + updated > 0) caches.evict(OperationResource.PREFIX, BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        return new ProductionSyncResult(inputs.size(), created, updated, skipped);
    }

    private OperationExample filterById(OperationEntity row) {
        var filter = new OperationExample();
        filter.createCriteria().andIdEqualTo(row.getId()).andFactoryIdEqualTo(row.getFactoryId());
        return filter;
    }

    private OperationEntity requireRecord(Long id) {
        OperationEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

