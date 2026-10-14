package dev.mesbase.business.issue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemResource;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemUpdateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueItemService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import dev.mesbase.business.issue.service.MaterialIssueRules;
import org.springframework.transaction.annotation.Transactional;

/**
 * 领料出库明细 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesMaterialIssueItemServiceImpl implements IMesMaterialIssueItemService {
    private final MesMaterialIssueItemMapper mapper;
    private final MaterialIssueRules rules;

    public MesMaterialIssueItemServiceImpl(MesMaterialIssueItemMapper mapper, MaterialIssueRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueItemResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesMaterialIssueItemPage selectPage(MesMaterialIssueItemQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesMaterialIssueItemExample example = new MesMaterialIssueItemExample();
        MesMaterialIssueItemExample.Criteria criteria = example.createCriteria();
        if (query.issueId() != null) {
            criteria.andIssueIdEqualTo(query.issueId());
        }
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.materialCode() != null) {
            criteria.andMaterialCodeLike("%" + query.materialCode() + "%");
        }
        if (query.batchNo() != null) {
            criteria.andBatchNoLike("%" + query.batchNo() + "%");
        }
        if (query.quantity() != null) {
            criteria.andQuantityEqualTo(query.quantity());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo(query.unitId());
        }
        if (query.unitCode() != null) {
            criteria.andUnitCodeLike("%" + query.unitCode() + "%");
        }
        if (query.warehouseId() != null) {
            criteria.andWarehouseIdEqualTo(query.warehouseId());
        }
        if (query.warehouseCode() != null) {
            criteria.andWarehouseCodeLike("%" + query.warehouseCode() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id");
        try (Page<MesMaterialIssueItemEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesMaterialIssueItemEntity> rows = mapper.selectByExample(example);
            return new MesMaterialIssueItemPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueItemResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesMaterialIssueItemEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesMaterialIssueItemEntity insert(MesMaterialIssueItemCreateCommand input) {
        MesMaterialIssueItemEntity row = new MesMaterialIssueItemEntity();

        row.setIssueId(input.issueId());
        row.setMaterialId(input.materialId());
        row.setBatchNo(input.batchNo());
        row.setQuantity(input.quantity());
        row.setUnitId(input.unitId());
        row.setWarehouseId(input.warehouseId());
        row.setRemark(input.remark());
        rules.validate(row);
        rules.audit(row, true);
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueItemEntity updateById(Long id, MesMaterialIssueItemUpdateCommand input) {
        MesMaterialIssueItemEntity row = requireRecord(id);
        rules.lockIssues(List.of(row.getIssueId()));
        if (!Objects.equals(row.getIssueId(), input.issueId())) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        row.setId(id);
        row.setIssueId(input.issueId());
        row.setMaterialId(input.materialId());
        row.setBatchNo(input.batchNo());
        row.setQuantity(input.quantity());
        row.setUnitId(input.unitId());
        row.setWarehouseId(input.warehouseId());
        row.setRemark(input.remark());
        rules.validate(row);
        rules.audit(row, false);
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueItemResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesMaterialIssueItemExample example = new MesMaterialIssueItemExample();
        rules.requireEditableIssues(ids.stream().map(id -> requireRecord(id).getIssueId()).distinct().sorted()
                .toList());
        rules.changedExtra(ids.stream().map(id -> requireRecord(id).getIssueId()).distinct().sorted().toList());
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesMaterialIssueItemEntity requireRecord(Long id) {
        MesMaterialIssueItemEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

