package dev.mesbase.business.wm.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.domain.WmInventoryCreateCommand;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmInventoryPage;
import dev.mesbase.business.wm.domain.WmInventoryQueryDto;
import dev.mesbase.business.wm.domain.WmInventoryResource;
import dev.mesbase.business.wm.domain.WmInventoryUpdateCommand;
import dev.mesbase.business.wm.service.IWmInventoryService;
import dev.mesbase.business.wm.sync.ExternalWmInventory;
import dev.mesbase.business.wm.sync.UnconfiguredWmInventoryQueryAdapter;
import dev.mesbase.business.wm.sync.WmInventoryQueryPort;
import dev.mesbase.business.wm.sync.WmInventorySyncResult;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存管理 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WmInventoryServiceImpl implements IWmInventoryService {
    private final StockReferenceRules rules;
    private final WmInventoryMapper mapper;
    private final WmInventoryQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IWmInventoryService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public WmInventoryServiceImpl(WmInventoryMapper mapper,
                                  ObjectProvider<WmInventoryQueryPort> queries,
                                  SyncCoordinator coordinator,
                                  ObjectProvider<IWmInventoryService> self,
                                  Validator validator, BusinessCacheProcessor caches, StockReferenceRules rules) {
        this.rules = rules;
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredWmInventoryQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WmInventoryResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public WmInventoryPage selectPage(WmInventoryQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        WmInventoryExample example = new WmInventoryExample();
        WmInventoryExample.Criteria criteria = example.createCriteria();
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.warehouseId() != null) {
            criteria.andWarehouseIdEqualTo(query.warehouseId());
        }
        if (query.quantity() != null) {
            criteria.andQuantityEqualTo(query.quantity());
        }
        if (query.availableQuantity() != null) {
            criteria.andAvailableQuantityEqualTo(query.availableQuantity());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id");
        try (Page<WmInventoryEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<WmInventoryEntity> rows = mapper.selectByExample(example);
            long total = page.getTotal();
            PageHelper.clearPage();
            return new WmInventoryPage(rules.describe(rows), total, offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WmInventoryResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public WmInventoryEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public WmInventoryEntity insert(WmInventoryCreateCommand input) {
        WmInventoryEntity row = new WmInventoryEntity();

        row.setMaterialId(input.materialId());
        row.setWarehouseId(input.warehouseId());
        row.setQuantity(input.quantity());
        row.setAvailableQuantity(input.availableQuantity());
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
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmInventoryEntity updateById(Long id, WmInventoryUpdateCommand input) {
        WmInventoryEntity row = requireRecord(id);
        row.setId(id);
        row.setMaterialId(input.materialId());
        row.setWarehouseId(input.warehouseId());
        row.setQuantity(input.quantity());
        row.setAvailableQuantity(input.availableQuantity());
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
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        WmInventoryExample example = new WmInventoryExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private WmInventoryEntity requireRecord(Long id) {
        WmInventoryEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public WmInventorySyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.wm_inventory", Duration.ofMinutes(5));
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
    public WmInventorySyncResult upsert(FactoryScope scope,
                                        List<ExternalWmInventory> inputs, SyncCoordinator.Run run) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        // EXTRA_CODE_REASON: sync writes stop at processing/commit boundaries after run-token loss.
        if (run == null || !scope.equals(run.scope()) || !"codegen.wm_inventory".equals(run.syncKey()))
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
        // EXTRA_CODE_REASON: inventory grain is Factory + Material + Warehouse.
        var keys = new HashSet<StockKey>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input)
                    .isEmpty() || !keys.add(new StockKey(input.materialId(), input.warehouseId()))) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new WmInventorySyncResult(0, 0, 0, 0);
        var example = new WmInventoryExample();
        example.createCriteria().andMaterialIdIn(keys.stream().map(StockKey::materialId).distinct().toList())

        ;
        var existing = new HashMap<StockKey, WmInventoryEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(new StockKey(row.getMaterialId(), row.getWarehouseId()), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        rules.validateBatch(inputs);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            coordinator.assertOwner(run);
            var row = existing.get(new StockKey(input.materialId(), input.warehouseId()));
            if (row != null && same(row, input)) {
                rules.validateInventoryFields(row);
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new WmInventoryEntity();

            }
            row.setMaterialId(input.materialId());
            row.setWarehouseId(input.warehouseId());
            row.setQuantity(input.quantity());
            row.setAvailableQuantity(input.availableQuantity());
            row.setStatus(input.status());
            row.setRemark(input.remark());
            rules.validateInventoryFields(row);
            audit(row, insert);
            int affected = write(row, insert);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(WmInventoryResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(WmInventoryResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new WmInventorySyncResult(inputs.size(), created, updated, skipped);
    }

    private record StockKey(Long materialId, Long warehouseId) {
    }

    // EXTRA_CODE_REASON: unique-key conflicts must be stable business errors for CRUD and sync.
    private int write(WmInventoryEntity row, boolean insert) {
        try {
            return insert ? mapper.insert(row) : mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.STOCK_KEY_DUPLICATE, failure);
        }
    }

    private void audit(WmInventoryEntity row, boolean insert) {
        var now = LocalDateTime.now();
        String user = String.valueOf(SecurityUtils.getUserId());
        if (insert) {
            row.setCreateBy(user);
            row.setCreateTime(now);
        }
        row.setUpdateBy(user);
        row.setUpdateTime(now);
    }

    private static boolean same(WmInventoryEntity row, ExternalWmInventory input) {
        return Objects.deepEquals(row.getMaterialId(), input.materialId())
                && Objects.deepEquals(row.getWarehouseId(), input.warehouseId())
                && (row.getQuantity() == null ? input.quantity() == null
                : input.quantity() != null && row.getQuantity().compareTo(input.quantity()) == 0)
                && (row.getAvailableQuantity() == null ? input.availableQuantity() == null
                : input.availableQuantity() != null && row.getAvailableQuantity()
                .compareTo(input.availableQuantity()) == 0)
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark());
    }
}

