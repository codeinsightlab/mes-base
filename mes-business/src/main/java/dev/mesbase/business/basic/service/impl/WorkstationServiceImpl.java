package dev.mesbase.business.basic.service.impl;

import dev.mesbase.business.basic.domain.WorkstationPage;
import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.basic.domain.WorkstationCreateCommand;
import dev.mesbase.business.basic.domain.WorkstationEntity;
import dev.mesbase.business.basic.domain.WorkstationExample;
import dev.mesbase.business.basic.domain.WorkstationQueryDto;
import dev.mesbase.business.basic.domain.WorkstationResource;
import dev.mesbase.business.basic.domain.WorkstationUpdateCommand;
import dev.mesbase.business.basic.service.IWorkstationService;
import dev.mesbase.business.basic.sync.ExternalWorkstation;
import dev.mesbase.business.basic.sync.UnconfiguredWorkstationQueryAdapter;
import dev.mesbase.business.basic.sync.WorkstationQueryPort;
import dev.mesbase.business.basic.sync.WorkstationSyncResult;
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
 * 工作站 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkstationServiceImpl implements IWorkstationService {
    private final WorkstationMapper mapper;
    private final BasicDataRules rules;
    private final WorkstationQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IWorkstationService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public WorkstationServiceImpl(WorkstationMapper mapper, BasicDataRules rules,
                                  ObjectProvider<WorkstationQueryPort> queries,
                                  SyncCoordinator coordinator,
                                  ObjectProvider<IWorkstationService> self,
                                  Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.rules = rules;
        this.query = queries.getIfAvailable(UnconfiguredWorkstationQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WorkstationResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public WorkstationPage selectPage(WorkstationQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        WorkstationExample example = new WorkstationExample();
        WorkstationExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.workshopId() != null) {
            criteria.andWorkshopIdEqualTo(query.workshopId());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<WorkstationEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<WorkstationEntity> rows = mapper.selectByExample(example);
            return new WorkstationPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WorkstationResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public WorkstationEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkstationResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public WorkstationEntity insert(WorkstationCreateCommand input) {
        WorkstationEntity row = new WorkstationEntity();

        row.setCode(input.code());
        row.setName(input.name());
        row.setWorkshopId(input.workshopId());
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
    @BusinessCacheEvict(business = WorkstationResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WorkstationEntity updateById(Long id, WorkstationUpdateCommand input) {
        WorkstationEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setWorkshopId(input.workshopId());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        rules.validate(row);
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WorkstationResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingWorkstation(ids);
        WorkstationExample example = new WorkstationExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(WorkstationEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(WorkstationEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private WorkstationEntity requireRecord(Long id) {
        WorkstationEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public WorkstationSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.workstation", Duration.ofMinutes(5));
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
    public WorkstationSyncResult upsert(FactoryScope scope,
                                        List<ExternalWorkstation> inputs) {
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
        if (inputs.isEmpty()) return new WorkstationSyncResult(0, 0, 0, 0);
        var example = new WorkstationExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))

        ;
        var existing = new HashMap<String, WorkstationEntity>();
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
                row = new WorkstationEntity();

            }
            row.setCode(input.code());
            row.setName(input.name());
            row.setWorkshopId(input.workshopId());
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
            caches.evict(WorkstationResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(WorkstationResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new WorkstationSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(WorkstationEntity row, ExternalWorkstation input) {
        return Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getWorkshopId(), input.workshopId())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark());
    }
}

