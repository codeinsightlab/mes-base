// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.dv.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.dv.dao.DvSubjectMapper;
import dev.mesbase.business.dv.domain.DvSubjectCreateCommand;
import dev.mesbase.business.dv.domain.DvSubjectEntity;
import dev.mesbase.business.dv.domain.DvSubjectExample;
import dev.mesbase.business.dv.domain.DvSubjectPage;
import dev.mesbase.business.dv.domain.DvSubjectQueryDto;
import dev.mesbase.business.dv.domain.DvSubjectResource;
import dev.mesbase.business.dv.domain.DvSubjectUpdateCommand;
import dev.mesbase.business.dv.service.IDvSubjectService;
import dev.mesbase.business.dv.sync.DvSubjectQueryPort;
import dev.mesbase.business.dv.sync.DvSubjectSyncResult;
import dev.mesbase.business.dv.sync.ExternalDvSubject;
import dev.mesbase.business.dv.sync.UnconfiguredDvSubjectQueryAdapter;
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
 * 设备点检保养项目 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class DvSubjectServiceImpl implements IDvSubjectService {
    private final DvSubjectMapper mapper;
    private final DvSubjectQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IDvSubjectService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public DvSubjectServiceImpl(DvSubjectMapper mapper,
                                ObjectProvider<DvSubjectQueryPort> queries,
                                SyncCoordinator coordinator,
                                ObjectProvider<IDvSubjectService> self,
                                Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredDvSubjectQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = DvSubjectResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public DvSubjectPage selectPage(DvSubjectQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        DvSubjectExample example = new DvSubjectExample();
        DvSubjectExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.subjectCode() != null) {
            criteria.andSubjectCodeLike("%" + query.subjectCode() + "%");
        }
        if (query.subjectName() != null) {
            criteria.andSubjectNameLike("%" + query.subjectName() + "%");
        }
        if (query.subjectType() != null) {
            criteria.andSubjectTypeLike("%" + query.subjectType() + "%");
        }
        if (query.subjectContent() != null) {
            criteria.andSubjectContentLike("%" + query.subjectContent() + "%");
        }
        if (query.subjectStandard() != null) {
            criteria.andSubjectStandardLike("%" + query.subjectStandard() + "%");
        }
        if (query.enableFlag() != null) {
            criteria.andEnableFlagLike("%" + query.enableFlag() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("subject_id");
        try (Page<DvSubjectEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<DvSubjectEntity> rows = mapper.selectByExample(example);
            return new DvSubjectPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = DvSubjectResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public DvSubjectEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = DvSubjectResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public DvSubjectEntity insert(DvSubjectCreateCommand input) {
        DvSubjectEntity row = new DvSubjectEntity();
        row.setFactoryId(input.factoryId());
        row.setSubjectCode(input.subjectCode());
        row.setSubjectName(input.subjectName());
        row.setSubjectType(input.subjectType());
        row.setSubjectContent(input.subjectContent());
        row.setSubjectStandard(input.subjectStandard());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getSubjectId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = DvSubjectResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public DvSubjectEntity updateById(Long id, DvSubjectUpdateCommand input) {
        DvSubjectEntity row = requireRecord(id);
        row.setSubjectId(id);
        row.setFactoryId(input.factoryId());
        row.setSubjectCode(input.subjectCode());
        row.setSubjectName(input.subjectName());
        row.setSubjectType(input.subjectType());
        row.setSubjectContent(input.subjectContent());
        row.setSubjectStandard(input.subjectStandard());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = DvSubjectResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        DvSubjectExample example = new DvSubjectExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andSubjectIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private DvSubjectEntity requireRecord(Long id) {
        DvSubjectEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public DvSubjectSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.dv_subject", Duration.ofMinutes(5));
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
    public DvSubjectSyncResult upsert(FactoryScope scope,
                                      List<ExternalDvSubject> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.subjectCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new DvSubjectSyncResult(0, 0, 0, 0);
        var example = new DvSubjectExample();
        example.createCriteria().andSubjectCodeIn(List.copyOf(keys))
        ;
        var existing = new HashMap<String, DvSubjectEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getSubjectCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.subjectCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new DvSubjectEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setSubjectCode(input.subjectCode());
            row.setSubjectName(input.subjectName());
            row.setSubjectType(input.subjectType());
            row.setSubjectContent(input.subjectContent());
            row.setSubjectStandard(input.subjectStandard());
            row.setEnableFlag(input.enableFlag());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(DvSubjectResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(DvSubjectResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new DvSubjectSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(DvSubjectEntity row, ExternalDvSubject input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getSubjectCode(), input.subjectCode())
                && Objects.deepEquals(row.getSubjectName(), input.subjectName())
                && Objects.deepEquals(row.getSubjectType(), input.subjectType())
                && Objects.deepEquals(row.getSubjectContent(), input.subjectContent())
                && Objects.deepEquals(row.getSubjectStandard(), input.subjectStandard())
                && Objects.deepEquals(row.getEnableFlag(), input.enableFlag())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

