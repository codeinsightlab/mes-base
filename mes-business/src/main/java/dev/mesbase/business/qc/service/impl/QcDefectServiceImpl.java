// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.qc.dao.QcDefectMapper;
import dev.mesbase.business.qc.domain.QcDefectCreateCommand;
import dev.mesbase.business.qc.domain.QcDefectEntity;
import dev.mesbase.business.qc.domain.QcDefectExample;
import dev.mesbase.business.qc.domain.QcDefectPage;
import dev.mesbase.business.qc.domain.QcDefectQueryDto;
import dev.mesbase.business.qc.domain.QcDefectResource;
import dev.mesbase.business.qc.domain.QcDefectUpdateCommand;
import dev.mesbase.business.qc.service.IQcDefectService;
import dev.mesbase.business.qc.sync.ExternalQcDefect;
import dev.mesbase.business.qc.sync.QcDefectQueryPort;
import dev.mesbase.business.qc.sync.QcDefectSyncResult;
import dev.mesbase.business.qc.sync.UnconfiguredQcDefectQueryAdapter;
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
 * 质量缺陷 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcDefectServiceImpl implements IQcDefectService {
    private final QcDefectMapper mapper;
    private final QcDefectQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IQcDefectService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public QcDefectServiceImpl(QcDefectMapper mapper,
                               ObjectProvider<QcDefectQueryPort> queries,
                               SyncCoordinator coordinator,
                               ObjectProvider<IQcDefectService> self,
                               Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredQcDefectQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = QcDefectResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public QcDefectPage selectPage(QcDefectQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        QcDefectExample example = new QcDefectExample();
        QcDefectExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.defectCode() != null) {
            criteria.andDefectCodeLike("%" + query.defectCode() + "%");
        }
        if (query.defectName() != null) {
            criteria.andDefectNameLike("%" + query.defectName() + "%");
        }
        if (query.indexType() != null) {
            criteria.andIndexTypeLike("%" + query.indexType() + "%");
        }
        if (query.defectLevel() != null) {
            criteria.andDefectLevelLike("%" + query.defectLevel() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("defect_id");
        try (Page<QcDefectEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<QcDefectEntity> rows = mapper.selectByExample(example);
            return new QcDefectPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = QcDefectResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public QcDefectEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = QcDefectResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public QcDefectEntity insert(QcDefectCreateCommand input) {
        QcDefectEntity row = new QcDefectEntity();
        row.setFactoryId(input.factoryId());
        row.setDefectCode(input.defectCode());
        row.setDefectName(input.defectName());
        row.setIndexType(input.indexType());
        row.setDefectLevel(input.defectLevel());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getDefectId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = QcDefectResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public QcDefectEntity updateById(Long id, QcDefectUpdateCommand input) {
        QcDefectEntity row = requireRecord(id);
        row.setDefectId(id);
        row.setFactoryId(input.factoryId());
        row.setDefectCode(input.defectCode());
        row.setDefectName(input.defectName());
        row.setIndexType(input.indexType());
        row.setDefectLevel(input.defectLevel());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = QcDefectResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        QcDefectExample example = new QcDefectExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andDefectIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private QcDefectEntity requireRecord(Long id) {
        QcDefectEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public QcDefectSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.qc_defect", Duration.ofMinutes(5));
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
    public QcDefectSyncResult upsert(FactoryScope scope,
                                     List<ExternalQcDefect> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.defectCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new QcDefectSyncResult(0, 0, 0, 0);
        var example = new QcDefectExample();
        example.createCriteria().andDefectCodeIn(List.copyOf(keys))
        ;
        var existing = new HashMap<String, QcDefectEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getDefectCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.defectCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new QcDefectEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setDefectCode(input.defectCode());
            row.setDefectName(input.defectName());
            row.setIndexType(input.indexType());
            row.setDefectLevel(input.defectLevel());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(QcDefectResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(QcDefectResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new QcDefectSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(QcDefectEntity row, ExternalQcDefect input) {
        return
                &&Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getDefectCode(), input.defectCode())
                && Objects.deepEquals(row.getDefectName(), input.defectName())
                && Objects.deepEquals(row.getIndexType(), input.indexType())
                && Objects.deepEquals(row.getDefectLevel(), input.defectLevel())
                && Objects.deepEquals(row.getRemark(), input.remark())
        ;
    }
}

