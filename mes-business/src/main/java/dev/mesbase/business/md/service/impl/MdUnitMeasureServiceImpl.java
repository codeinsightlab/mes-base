// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.md.dao.MdUnitMeasureMapper;
import dev.mesbase.business.md.domain.MdUnitMeasureCreateCommand;
import dev.mesbase.business.md.domain.MdUnitMeasureEntity;
import dev.mesbase.business.md.domain.MdUnitMeasureExample;
import dev.mesbase.business.md.domain.MdUnitMeasurePage;
import dev.mesbase.business.md.domain.MdUnitMeasureQueryDto;
import dev.mesbase.business.md.domain.MdUnitMeasureResource;
import dev.mesbase.business.md.domain.MdUnitMeasureUpdateCommand;
import dev.mesbase.business.md.service.IMdUnitMeasureService;
import dev.mesbase.business.md.sync.ExternalMdUnitMeasure;
import dev.mesbase.business.md.sync.MdUnitMeasureQueryPort;
import dev.mesbase.business.md.sync.MdUnitMeasureSyncResult;
import dev.mesbase.business.md.sync.UnconfiguredMdUnitMeasureQueryAdapter;
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
 * 计量单位 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdUnitMeasureServiceImpl implements IMdUnitMeasureService {
    private final MdUnitMeasureMapper mapper;
    private final MdUnitMeasureQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IMdUnitMeasureService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public MdUnitMeasureServiceImpl(MdUnitMeasureMapper mapper,
                                    ObjectProvider<MdUnitMeasureQueryPort> queries,
                                    SyncCoordinator coordinator,
                                    ObjectProvider<IMdUnitMeasureService> self,
                                    Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredMdUnitMeasureQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MdUnitMeasureResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MdUnitMeasurePage selectPage(MdUnitMeasureQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MdUnitMeasureExample example = new MdUnitMeasureExample();
        MdUnitMeasureExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.measureCode() != null) {
            criteria.andMeasureCodeLike("%" + query.measureCode() + "%");
        }
        if (query.measureName() != null) {
            criteria.andMeasureNameLike("%" + query.measureName() + "%");
        }
        if (query.primaryFlag() != null) {
            criteria.andPrimaryFlagLike("%" + query.primaryFlag() + "%");
        }
        if (query.primaryId() != null) {
            criteria.andPrimaryIdEqualTo(query.primaryId());
        }
        if (query.changeRate() != null) {
            criteria.andChangeRateEqualTo(query.changeRate());
        }
        if (query.enableFlag() != null) {
            criteria.andEnableFlagLike("%" + query.enableFlag() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("measure_id");
        try (Page<MdUnitMeasureEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MdUnitMeasureEntity> rows = mapper.selectByExample(example);
            return new MdUnitMeasurePage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MdUnitMeasureResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MdUnitMeasureEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MdUnitMeasureResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MdUnitMeasureEntity insert(MdUnitMeasureCreateCommand input) {
        MdUnitMeasureEntity row = new MdUnitMeasureEntity();
        row.setFactoryId(input.factoryId());
        row.setMeasureCode(input.measureCode());
        row.setMeasureName(input.measureName());
        row.setPrimaryFlag(input.primaryFlag());
        row.setPrimaryId(input.primaryId());
        row.setChangeRate(input.changeRate());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getMeasureId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MdUnitMeasureResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MdUnitMeasureEntity updateById(Long id, MdUnitMeasureUpdateCommand input) {
        MdUnitMeasureEntity row = requireRecord(id);
        row.setMeasureId(id);
        row.setFactoryId(input.factoryId());
        row.setMeasureCode(input.measureCode());
        row.setMeasureName(input.measureName());
        row.setPrimaryFlag(input.primaryFlag());
        row.setPrimaryId(input.primaryId());
        row.setChangeRate(input.changeRate());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MdUnitMeasureResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MdUnitMeasureExample example = new MdUnitMeasureExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andMeasureIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MdUnitMeasureEntity requireRecord(Long id) {
        MdUnitMeasureEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public MdUnitMeasureSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.md_unit_measure", Duration.ofMinutes(5));
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
    public MdUnitMeasureSyncResult upsert(FactoryScope scope,
                                          List<ExternalMdUnitMeasure> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.measureCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new MdUnitMeasureSyncResult(0, 0, 0, 0);
        var example = new MdUnitMeasureExample();
        example.createCriteria().andMeasureCodeIn(List.copyOf(keys))
        ;
        var existing = new HashMap<String, MdUnitMeasureEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getMeasureCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.measureCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new MdUnitMeasureEntity();
            }
            row.setFactoryId(input.factoryId());
            row.setMeasureCode(input.measureCode());
            row.setMeasureName(input.measureName());
            row.setPrimaryFlag(input.primaryFlag());
            row.setPrimaryId(input.primaryId());
            row.setChangeRate(input.changeRate());
            row.setEnableFlag(input.enableFlag());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(MdUnitMeasureResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(MdUnitMeasureResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new MdUnitMeasureSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MdUnitMeasureEntity row, ExternalMdUnitMeasure input) {
        return true
                && Objects.deepEquals(row.getFactoryId(), input.factoryId())
                && Objects.deepEquals(row.getMeasureCode(), input.measureCode())
                && Objects.deepEquals(row.getMeasureName(), input.measureName())
                && Objects.deepEquals(row.getPrimaryFlag(), input.primaryFlag())
                && Objects.deepEquals(row.getPrimaryId(), input.primaryId())
                && (row.getChangeRate() == null ? input.changeRate() == null
                : input.changeRate() != null && row.getChangeRate().compareTo(input.changeRate()) == 0)
                && Objects.deepEquals(row.getEnableFlag(), input.enableFlag())
                && Objects.deepEquals(row.getRemark(), input.remark())
                ;
    }
}

