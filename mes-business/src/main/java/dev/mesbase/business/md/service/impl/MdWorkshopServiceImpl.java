package dev.mesbase.business.md.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.md.dao.MdWorkshopMapper;
import dev.mesbase.business.md.domain.MdWorkshopCreateCommand;
import dev.mesbase.business.md.domain.MdWorkshopEntity;
import dev.mesbase.business.md.domain.MdWorkshopExample;
import dev.mesbase.business.md.domain.MdWorkshopPage;
import dev.mesbase.business.md.domain.MdWorkshopQueryDto;
import dev.mesbase.business.md.domain.MdWorkshopResource;
import dev.mesbase.business.md.domain.MdWorkshopUpdateCommand;
import dev.mesbase.business.md.service.IMdWorkshopService;
import dev.mesbase.business.md.sync.ExternalMdWorkshop;
import dev.mesbase.business.md.sync.MdWorkshopQueryPort;
import dev.mesbase.business.md.sync.MdWorkshopSyncResult;
import dev.mesbase.business.md.sync.UnconfiguredMdWorkshopQueryAdapter;
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
 * 车间 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdWorkshopServiceImpl implements IMdWorkshopService {
    private final MdWorkshopMapper mapper;
    private final MdWorkshopQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IMdWorkshopService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public MdWorkshopServiceImpl(MdWorkshopMapper mapper,
                                 ObjectProvider<MdWorkshopQueryPort> queries,
                                 SyncCoordinator coordinator,
                                 ObjectProvider<IMdWorkshopService> self,
                                 Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredMdWorkshopQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MdWorkshopResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MdWorkshopPage selectPage(MdWorkshopQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MdWorkshopExample example = new MdWorkshopExample();
        MdWorkshopExample.Criteria criteria = example.createCriteria();
        if (query.workshopCode() != null) {
            criteria.andWorkshopCodeLike("%" + query.workshopCode() + "%");
        }
        if (query.workshopName() != null) {
            criteria.andWorkshopNameLike("%" + query.workshopName() + "%");
        }
        if (query.area() != null) {
            criteria.andAreaEqualTo(query.area());
        }
        if (query.charge() != null) {
            criteria.andChargeLike("%" + query.charge() + "%");
        }
        if (query.deptId() != null) {
            criteria.andDeptIdEqualTo(query.deptId());
        }
        if (query.enableFlag() != null) {
            criteria.andEnableFlagLike("%" + query.enableFlag() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("workshop_id");
        try (Page<MdWorkshopEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MdWorkshopEntity> rows = mapper.selectByExample(example);
            return new MdWorkshopPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MdWorkshopResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MdWorkshopEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MdWorkshopResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MdWorkshopEntity insert(MdWorkshopCreateCommand input) {
        MdWorkshopEntity row = new MdWorkshopEntity();

        row.setWorkshopCode(input.workshopCode());
        row.setWorkshopName(input.workshopName());
        row.setArea(input.area());
        row.setCharge(input.charge());
        row.setDeptId(input.deptId());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getWorkshopId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MdWorkshopResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MdWorkshopEntity updateById(Long id, MdWorkshopUpdateCommand input) {
        MdWorkshopEntity row = requireRecord(id);
        row.setWorkshopId(id);

        row.setWorkshopCode(input.workshopCode());
        row.setWorkshopName(input.workshopName());
        row.setArea(input.area());
        row.setCharge(input.charge());
        row.setDeptId(input.deptId());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MdWorkshopResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MdWorkshopExample example = new MdWorkshopExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andWorkshopIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MdWorkshopEntity requireRecord(Long id) {
        MdWorkshopEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    public MdWorkshopSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.md_workshop", Duration.ofMinutes(5));
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
    public MdWorkshopSyncResult upsert(FactoryScope scope,
                                       List<ExternalMdWorkshop> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.workshopCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new MdWorkshopSyncResult(0, 0, 0, 0);
        var example = new MdWorkshopExample();
        example.createCriteria().andWorkshopCodeIn(List.copyOf(keys))
        ;
        var existing = new HashMap<String, MdWorkshopEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getWorkshopCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.workshopCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new MdWorkshopEntity();
            }

            row.setWorkshopCode(input.workshopCode());
            row.setWorkshopName(input.workshopName());
            row.setArea(input.area());
            row.setCharge(input.charge());
            row.setDeptId(input.deptId());
            row.setEnableFlag(input.enableFlag());
            row.setRemark(input.remark());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(MdWorkshopResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(MdWorkshopResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new MdWorkshopSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(MdWorkshopEntity row, ExternalMdWorkshop input) {
        return Objects.deepEquals(row.getWorkshopCode(), input.workshopCode())
                && Objects.deepEquals(row.getWorkshopName(), input.workshopName())
                && (row.getArea() == null ? input.area() == null
                : input.area() != null && row.getArea().compareTo(input.area()) == 0)
                && Objects.deepEquals(row.getCharge(), input.charge())
                && Objects.deepEquals(row.getDeptId(), input.deptId())
                && Objects.deepEquals(row.getEnableFlag(), input.enableFlag())
                && Objects.deepEquals(row.getRemark(), input.remark());
    }
}

