// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.qc.dao.QcIndexMapper;
import dev.mesbase.business.qc.domain.QcIndexCreateCommand;
import dev.mesbase.business.qc.domain.QcIndexEntity;
import dev.mesbase.business.qc.domain.QcIndexExample;
import dev.mesbase.business.qc.domain.QcIndexPage;
import dev.mesbase.business.qc.domain.QcIndexQueryDto;
import dev.mesbase.business.qc.domain.QcIndexResource;
import dev.mesbase.business.qc.domain.QcIndexUpdateCommand;
import dev.mesbase.business.qc.service.IQcIndexService;
import dev.mesbase.business.qc.sync.ExternalQcIndex;
import dev.mesbase.business.qc.sync.QcIndexQueryPort;
import dev.mesbase.business.qc.sync.QcIndexSyncResult;
import dev.mesbase.business.qc.sync.UnconfiguredQcIndexQueryAdapter;
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
 * 质量检测项 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcIndexServiceImpl implements IQcIndexService {
    private final QcIndexMapper mapper;
    private final QcIndexQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IQcIndexService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public QcIndexServiceImpl(QcIndexMapper mapper,
            ObjectProvider<QcIndexQueryPort> queries,
            SyncCoordinator coordinator,
            ObjectProvider<IQcIndexService> self,
            Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredQcIndexQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = QcIndexResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public QcIndexPage selectPage(QcIndexQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        QcIndexExample example = new QcIndexExample();
        QcIndexExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.indexCode() != null) {
            criteria.andIndexCodeLike("%" + query.indexCode() + "%");
        }
        if (query.indexName() != null) {
            criteria.andIndexNameLike("%" + query.indexName() + "%");
        }
        if (query.indexType() != null) {
            criteria.andIndexTypeLike("%" + query.indexType() + "%");
        }
        if (query.qcTool() != null) {
            criteria.andQcToolLike("%" + query.qcTool() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("index_id");
        try (Page<QcIndexEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<QcIndexEntity> rows = mapper.selectByExample(example);
            return new QcIndexPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = QcIndexResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public QcIndexEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = QcIndexResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public QcIndexEntity insert(QcIndexCreateCommand input) {
        QcIndexEntity row = new QcIndexEntity();
        row.setFactoryId(input.factoryId());
        row.setIndexCode(input.indexCode());
        row.setIndexName(input.indexName());
        row.setIndexType(input.indexType());
        row.setQcTool(input.qcTool());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getIndexId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = QcIndexResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public QcIndexEntity updateById(Long id, QcIndexUpdateCommand input) {
        QcIndexEntity row = requireRecord(id);
        row.setIndexId(id);
        row.setFactoryId(input.factoryId());
        row.setIndexCode(input.indexCode());
        row.setIndexName(input.indexName());
        row.setIndexType(input.indexType());
        row.setQcTool(input.qcTool());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = QcIndexResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        QcIndexExample example = new QcIndexExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIndexIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private QcIndexEntity requireRecord(Long id) {
        QcIndexEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
    public QcIndexSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.qc_index", Duration.ofMinutes(5));
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
    public QcIndexSyncResult upsert(FactoryScope scope,
                                       List<ExternalQcIndex> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.indexCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new QcIndexSyncResult(0, 0, 0, 0);
        var example = new QcIndexExample();
        example.createCriteria().andIndexCodeIn(List.copyOf(keys))
                ;
        var existing = new HashMap<String, QcIndexEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getIndexCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.indexCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new QcIndexEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setIndexCode(input.indexCode());
            row.setIndexName(input.indexName());
            row.setIndexType(input.indexType());
            row.setQcTool(input.qcTool());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(QcIndexResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(QcIndexResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new QcIndexSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(QcIndexEntity row, ExternalQcIndex input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getIndexCode(), input.indexCode())
                && Objects.deepEquals(row.getIndexName(), input.indexName())
                && Objects.deepEquals(row.getIndexType(), input.indexType())
                && Objects.deepEquals(row.getQcTool(), input.qcTool())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

