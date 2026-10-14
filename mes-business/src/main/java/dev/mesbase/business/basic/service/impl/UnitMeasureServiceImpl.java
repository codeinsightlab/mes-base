package dev.mesbase.business.basic.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.UnitMeasureCreateCommand;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import dev.mesbase.business.basic.domain.UnitMeasurePage;
import dev.mesbase.business.basic.domain.UnitMeasureQueryDto;
import dev.mesbase.business.basic.domain.UnitMeasureResource;
import dev.mesbase.business.basic.domain.UnitMeasureUpdateCommand;
import dev.mesbase.business.basic.service.IUnitMeasureService;
import dev.mesbase.business.basic.sync.ExternalUnitMeasure;
import dev.mesbase.business.basic.sync.UnconfiguredUnitMeasureQueryAdapter;
import dev.mesbase.business.basic.sync.UnitMeasureQueryPort;
import dev.mesbase.business.basic.sync.UnitMeasureSyncResult;
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
 * 计量单位 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class UnitMeasureServiceImpl implements IUnitMeasureService {
    private final UnitMeasureMapper mapper;
    private final BasicDataRules rules;
    private final UnitMeasureQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IUnitMeasureService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public UnitMeasureServiceImpl(UnitMeasureMapper mapper, BasicDataRules rules,
                                  ObjectProvider<UnitMeasureQueryPort> queries,
                                  SyncCoordinator coordinator,
                                  ObjectProvider<IUnitMeasureService> self,
                                  Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.rules = rules;
        this.query = queries.getIfAvailable(UnconfiguredUnitMeasureQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = UnitMeasureResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public UnitMeasurePage selectPage(UnitMeasureQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        UnitMeasureExample example = new UnitMeasureExample();
        UnitMeasureExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.symbol() != null) {
            criteria.andSymbolLike("%" + query.symbol() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        example.setOrderByClause("id");
        try (Page<UnitMeasureEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<UnitMeasureEntity> rows = mapper.selectByExample(example);
            return new UnitMeasurePage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = UnitMeasureResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public UnitMeasureEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = UnitMeasureResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public UnitMeasureEntity insert(UnitMeasureCreateCommand input) {
        UnitMeasureEntity row = new UnitMeasureEntity();

        row.setCode(input.code());
        row.setName(input.name());
        row.setSymbol(input.symbol());
        row.setStatus(input.status());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = UnitMeasureResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public UnitMeasureEntity updateById(Long id, UnitMeasureUpdateCommand input) {
        UnitMeasureEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setSymbol(input.symbol());
        row.setStatus(input.status());
        rules.validate(row);
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = UnitMeasureResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingUnitMeasure(ids);
        UnitMeasureExample example = new UnitMeasureExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(UnitMeasureEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(UnitMeasureEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private UnitMeasureEntity requireRecord(Long id) {
        UnitMeasureEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public UnitMeasureSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.unit_measure", Duration.ofMinutes(5));
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
    public UnitMeasureSyncResult upsert(FactoryScope scope,
                                        List<ExternalUnitMeasure> inputs) {
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
        if (inputs.isEmpty()) return new UnitMeasureSyncResult(0, 0, 0, 0);
        var example = new UnitMeasureExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))

        ;
        var existing = new HashMap<String, UnitMeasureEntity>();
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
                row = new UnitMeasureEntity();

            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setSymbol(input.symbol());
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
        if (updated > 0) {
            caches.evict(UnitMeasureResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(UnitMeasureResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new UnitMeasureSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(UnitMeasureEntity row, ExternalUnitMeasure input) {
        return Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getSymbol(), input.symbol())
                && Objects.deepEquals(row.getStatus(), input.status());
    }
}

