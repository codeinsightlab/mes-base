// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.WorkshopMapper;
import dev.mesbase.business.basic.domain.WorkshopCreateCommand;
import dev.mesbase.business.basic.domain.WorkshopEntity;
import dev.mesbase.business.basic.domain.WorkshopExample;
import dev.mesbase.business.basic.domain.WorkshopPage;
import dev.mesbase.business.basic.domain.WorkshopQueryDto;
import dev.mesbase.business.basic.domain.WorkshopResource;
import dev.mesbase.business.basic.domain.WorkshopUpdateCommand;
import dev.mesbase.business.basic.service.IWorkshopService;
import dev.mesbase.business.basic.sync.ExternalWorkshop;
import dev.mesbase.business.basic.sync.UnconfiguredWorkshopQueryAdapter;
import dev.mesbase.business.basic.sync.WorkshopQueryPort;
import dev.mesbase.business.basic.sync.WorkshopSyncResult;
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

/**
 * 车间 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkshopServiceImpl implements IWorkshopService {
    private final WorkshopMapper mapper;
    private final BasicDataRules rules;
    private final WorkshopQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IWorkshopService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public WorkshopServiceImpl(WorkshopMapper mapper, BasicDataRules rules,
                               ObjectProvider<WorkshopQueryPort> queries,
                               SyncCoordinator coordinator,
                               ObjectProvider<IWorkshopService> self,
                               Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.rules = rules;
        this.query = queries.getIfAvailable(UnconfiguredWorkshopQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WorkshopResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public WorkshopPage selectPage(WorkshopQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        WorkshopExample example = new WorkshopExample();
        WorkshopExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id");
        try (Page<WorkshopEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<WorkshopEntity> rows = mapper.selectByExample(example);
            return new WorkshopPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WorkshopResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public WorkshopEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkshopResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public WorkshopEntity insert(WorkshopCreateCommand input) {
        WorkshopEntity row = new WorkshopEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkshopResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WorkshopEntity updateById(Long id, WorkshopUpdateCommand input) {
        WorkshopEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        rules.validate(row);
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkshopResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingWorkshop(ids);
        WorkshopExample example = new WorkshopExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(WorkshopEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(WorkshopEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private WorkshopEntity requireRecord(Long id) {
        WorkshopEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public WorkshopSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.workshop", Duration.ofMinutes(5));
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
    public WorkshopSyncResult upsert(FactoryScope scope,
                                     List<ExternalWorkshop> inputs) {
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
        if (inputs.isEmpty()) return new WorkshopSyncResult(0, 0, 0, 0);
        var example = new WorkshopExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))
                .andFactoryIdEqualTo(scope.factoryId().value())
        ;
        var existing = new HashMap<String, WorkshopEntity>();
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
                row = new WorkshopEntity();
                row.setFactoryId(scope.factoryId().value());
            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setStatus(input.status());
            row.setRemark(input.remark());
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
            caches.evict(WorkshopResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(WorkshopResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new WorkshopSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(WorkshopEntity row, ExternalWorkshop input) {
        return Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark());
    }
}

