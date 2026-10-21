package dev.mesbase.business.wm.service.impl;

import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.ExternalSyncAction;
import dev.mesbase.business.shared.ExternalSyncCheck;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmWarehouseCreateCommand;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.domain.WmWarehousePage;
import dev.mesbase.business.wm.domain.WmWarehouseQueryDto;
import dev.mesbase.business.wm.domain.WmWarehouseResource;
import dev.mesbase.business.wm.domain.WmWarehouseUpdateCommand;
import dev.mesbase.business.wm.service.IWmWarehouseService;
import dev.mesbase.business.wm.sync.ExternalWmWarehouse;
import dev.mesbase.business.wm.sync.UnconfiguredWmWarehouseQueryAdapter;
import dev.mesbase.business.wm.sync.WmWarehouseQueryPort;
import dev.mesbase.business.wm.sync.WmWarehouseSyncResult;
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
import java.time.LocalDateTime;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.common.utils.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.business.wm.domain.WmWarehouseErpExtraEntity;
import dev.mesbase.business.wm.domain.WmWarehouseErpExtraExample;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仓库管理 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WmWarehouseServiceImpl implements IWmWarehouseService {
    private final BusinessSourceRules sources;
    private final WmWarehouseErpExtraMapper external;
    private final StockReferenceRules rules;
    private final WmWarehouseMapper mapper;
    private final WmWarehouseQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IWmWarehouseService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public WmWarehouseServiceImpl(WmWarehouseMapper mapper,
                                  ObjectProvider<WmWarehouseQueryPort> queries,
                                  SyncCoordinator coordinator,
                                  ObjectProvider<IWmWarehouseService> self,
                                  Validator validator, BusinessCacheProcessor caches, StockReferenceRules rules,
                                  BusinessSourceRules sources, WmWarehouseErpExtraMapper external) {
        this.external = external;
        this.sources = sources;
        this.rules = rules;
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredWmWarehouseQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WmWarehouseResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public WmWarehousePage selectPage(WmWarehouseQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        WmWarehouseExample example = new WmWarehouseExample();
        WmWarehouseExample.Criteria criteria = example.createCriteria();
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
        if (query.source() != null) criteria.andSourceEqualTo(BusinessSourceRules.required(query.source()).getValue());
        example.setOrderByClause("id desc");
        try (Page<WmWarehouseEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<WmWarehouseEntity> rows = mapper.selectByExample(example);
            return new WmWarehousePage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WmWarehouseResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public WmWarehouseEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmWarehouseResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public WmWarehouseEntity insert(WmWarehouseCreateCommand input) {
        sources.localCreation();
        WmWarehouseEntity row = new WmWarehouseEntity();
        row.setSource(BusinessSource.LOCAL.getValue());

        row.setCode(input.code());
        row.setName(input.name());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        rules.validate(row);
        audit(row, true);
        if (write(row, true) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmWarehouseResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmWarehouseEntity updateById(Long id, WmWarehouseUpdateCommand input) {
        WmWarehouseEntity row = requireRecord(id);
        BusinessSourceRules.localEditable(row.getSource());
        sources.current(row.getSource());
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        rules.validate(row);
        audit(row, false);
        if (write(row, false) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmWarehouseResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingWarehouses(ids);
        WmWarehouseExample example = new WmWarehouseExample();
        for (var id : ids) {
            var row = requireRecord(id);
            BusinessSourceRules.localEditable(row.getSource());
            sources.current(row.getSource());
        }
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private WmWarehouseEntity requireRecord(Long id) {
        WmWarehouseEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, noRollbackFor = FoundationException.class)
    public ExternalSyncCheck inspectExternal(ExternalWmWarehouse input) {
        return checkExternal(input);
    }

    private ExternalSyncCheck checkExternal(ExternalWmWarehouse input) {
        sources.current(BusinessSource.EXTERNAL.getValue());
        if (input == null || !validator.validate(input).isEmpty())
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var sample = new WmWarehouseEntity();
        sample.setStatus(input.status());
        rules.validate(sample);
        var filter = new WmWarehouseExample();
        filter.createCriteria().andCodeEqualTo(input.code());
        var found = mapper.selectByExample(filter);
        if (found.size() > 1) throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var snapshot = FactoryIntegrationContext.required();
        var ids = new WmWarehouseErpExtraExample();
        ids.createCriteria().andExternalIdEqualTo(input.externalId()).andProviderEqualTo(snapshot.erpType())
                .andBindingKeyEqualTo(snapshot.configurationRef());
        var identities = external.selectByExample(ids);
        if (identities.size() > 1 || identities.stream().anyMatch(x -> found.isEmpty()
                || !Objects.equals(x.getId(), found.getFirst().getId())))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (found.isEmpty()) return new ExternalSyncCheck(ExternalSyncAction.CREATE, null);
        var row = found.getFirst();
        if (BusinessSourceRules.required(row.getSource()) != BusinessSource.EXTERNAL)
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var identity = external.selectByPrimaryKey(row.getId());
        if (identity == null || !Objects.equals(identity.getExternalId(), input.externalId()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        BusinessSourceRules.binding(identity.getProvider(), identity.getBindingKey(), identity.getConfigurationRevision());
        return new ExternalSyncCheck(same(row, input) ? ExternalSyncAction.UNCHANGED : ExternalSyncAction.UPDATE, row.getId());
    }

    public WmWarehouseSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        sources.current(BusinessSource.EXTERNAL.getValue());
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.wm_warehouse", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var result = self.getObject().upsert(scope, inputs, run);
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
    public WmWarehouseSyncResult upsert(FactoryScope scope,
                                        List<ExternalWmWarehouse> inputs, SyncCoordinator.Run run) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        sources.current(BusinessSource.EXTERNAL.getValue());
        // EXTRA_CODE_REASON: sync writes stop at processing/commit boundaries after run-token loss.
        if (run == null || !scope.equals(run.scope()) || !"codegen.wm_warehouse".equals(run.syncKey()))
            throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
        coordinator.assertOwner(run);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                coordinator.assertOwner(run);
            }
        });
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        var externalKeys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input)
                    .isEmpty() || !keys.add(input.code()) || !externalKeys.add(input.externalId())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        for (var input : inputs) checkExternal(input);
        if (inputs.isEmpty()) return new WmWarehouseSyncResult(0, 0, 0, 0);
        var example = new WmWarehouseExample();
        example.createCriteria().andCodeIn(List.copyOf(keys))

        ;
        var existing = new HashMap<String, WmWarehouseEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        var mappingFilter = new WmWarehouseErpExtraExample();
        var currentSnapshot = FactoryIntegrationContext.required();
        mappingFilter.createCriteria().andExternalIdIn(List.copyOf(externalKeys))
                .andProviderEqualTo(currentSnapshot.erpType()).andBindingKeyEqualTo(currentSnapshot.configurationRef());
        var knownMappings = external.selectByExample(mappingFilter);
        var knownById = new HashMap<Long, WmWarehouseErpExtraEntity>();
        for (var mapping : knownMappings) {
            if (knownById.put(mapping.getId(), mapping) != null)
                throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        }
        for (var input : inputs)
            for (var known : knownMappings) {
                if (Objects.equals(input.externalId(), known.getExternalId()) && (existing.get(input.code()) == null
                        || !Objects.equals(existing.get(input.code()).getId(), known.getId())))
                    throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            coordinator.assertOwner(run);
            var row = existing.get(input.code());
            if (row != null && BusinessSourceRules.required(row.getSource()) != BusinessSource.EXTERNAL)
                throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            var snapshot = FactoryIntegrationContext.required();
            var mapping = row == null ? null : knownById.get(row.getId());
            if (row != null && (mapping == null || !Objects.equals(mapping.getExternalId(), input.externalId())))
                throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            if (mapping != null)
                BusinessSourceRules.binding(mapping.getProvider(), mapping.getBindingKey(), mapping.getConfigurationRevision());
            if (row != null && same(row, input)) {
                rules.validate(row);
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new WmWarehouseEntity();

            }
            row.setSource(BusinessSource.EXTERNAL.getValue());
            row.setCode(input.code());
            row.setName(input.name());
            row.setStatus(input.status());
            row.setRemark(input.remark());
            rules.validate(row);
            audit(row, insert);
            int affected = write(row, insert);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) {
                mapping = new WmWarehouseErpExtraEntity();
                mapping.setId(row.getId());
                mapping.setExternalId(input.externalId());
                mapping.setProvider(snapshot.erpType());
                mapping.setBindingKey(snapshot.configurationRef());
                mapping.setConfigurationRevision(snapshot.configurationRevision());
                if (external.insert(mapping) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(WmWarehouseResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(WmWarehouseResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new WmWarehouseSyncResult(inputs.size(), created, updated, skipped);
    }

    // EXTRA_CODE_REASON: unique-key conflicts must be stable business errors for CRUD and sync.
    private int write(WmWarehouseEntity row, boolean insert) {
        try {
            return insert ? mapper.insert(row) : mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.STOCK_KEY_DUPLICATE, failure);
        }
    }

    private void audit(WmWarehouseEntity row, boolean insert) {
        var now = LocalDateTime.now();
        String user = String.valueOf(SecurityUtils.getUserId());
        if (insert) {
            row.setCreateBy(user);
            row.setCreateTime(now);
        }
        row.setUpdateBy(user);
        row.setUpdateTime(now);
    }

    private static boolean same(WmWarehouseEntity row, ExternalWmWarehouse input) {
        return Objects.deepEquals(row.getCode(), input.code())
                && Objects.deepEquals(row.getName(), input.name())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark());
    }
}

