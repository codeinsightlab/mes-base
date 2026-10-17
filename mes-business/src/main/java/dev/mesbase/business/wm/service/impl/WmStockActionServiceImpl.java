// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.wm.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.domain.WmStockActionCreateCommand;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.wm.domain.WmStockActionPage;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.domain.WmStockActionResource;
import dev.mesbase.business.wm.domain.WmStockActionUpdateCommand;
import dev.mesbase.business.wm.service.IWmStockActionService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地仓储不可变收发存动作事实 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WmStockActionServiceImpl implements IWmStockActionService {
    private final WmStockActionMapper mapper;

    public WmStockActionServiceImpl(WmStockActionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WmStockActionResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public WmStockActionPage selectPage(WmStockActionQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        WmStockActionExample example = new WmStockActionExample();
        WmStockActionExample.Criteria criteria = example.createCriteria();
        if (query.requestHash() != null) {
            criteria.andRequestHashLike("%" + query.requestHash() + "%");
        }
        if (query.stockId() != null) {
            criteria.andStockIdEqualTo( query.stockId());
        }
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo( query.materialId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo( query.unitId());
        }
        if (query.warehouseId() != null) {
            criteria.andWarehouseIdEqualTo( query.warehouseId());
        }
        if (query.batchNo() != null) {
            criteria.andBatchNoLike("%" + query.batchNo() + "%");
        }
        if (query.binCode() != null) {
            criteria.andBinCodeLike("%" + query.binCode() + "%");
        }
        if (query.actionType() != null) {
            criteria.andActionTypeLike("%" + query.actionType() + "%");
        }
        if (query.quantityDelta() != null) {
            criteria.andQuantityDeltaEqualTo( query.quantityDelta());
        }
        if (query.availableDelta() != null) {
            criteria.andAvailableDeltaEqualTo( query.availableDelta());
        }
        if (query.quantityBefore() != null) {
            criteria.andQuantityBeforeEqualTo( query.quantityBefore());
        }
        if (query.quantityAfter() != null) {
            criteria.andQuantityAfterEqualTo( query.quantityAfter());
        }
        if (query.availableBefore() != null) {
            criteria.andAvailableBeforeEqualTo( query.availableBefore());
        }
        if (query.availableAfter() != null) {
            criteria.andAvailableAfterEqualTo( query.availableAfter());
        }
        if (query.sourceType() != null) {
            criteria.andSourceTypeLike("%" + query.sourceType() + "%");
        }
        if (query.sourceId() != null) {
            criteria.andSourceIdLike("%" + query.sourceId() + "%");
        }
        if (query.referenceActionId() != null) {
            criteria.andReferenceActionIdLike("%" + query.referenceActionId() + "%");
        }
        if (query.executionId() != null) {
            criteria.andExecutionIdEqualTo( query.executionId());
        }
        if (query.operatorUserId() != null) {
            criteria.andOperatorUserIdEqualTo( query.operatorUserId());
        }
        if (query.occurredAt() != null) {
            criteria.andOccurredAtEqualTo( query.occurredAt());
        }
        if (query.recordedAt() != null) {
            criteria.andRecordedAtEqualTo( query.recordedAt());
        }
        if (query.reason() != null) {
            criteria.andReasonLike("%" + query.reason() + "%");
        }
        example.setOrderByClause("id");
        try (Page<WmStockActionEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<WmStockActionEntity> rows = mapper.selectByExample(example);
            return new WmStockActionPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = WmStockActionResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public WmStockActionEntity selectById(String id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmStockActionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public WmStockActionEntity insert(WmStockActionCreateCommand input) {
        WmStockActionEntity row = new WmStockActionEntity();
        row.setId(input.id());
        row.setRequestHash(input.requestHash());
        row.setStockId(input.stockId());
        row.setMaterialId(input.materialId());
        row.setUnitId(input.unitId());
        row.setWarehouseId(input.warehouseId());
        row.setBatchNo(input.batchNo());
        row.setBinCode(input.binCode());
        row.setActionType(input.actionType());
        row.setQuantityDelta(input.quantityDelta());
        row.setAvailableDelta(input.availableDelta());
        row.setQuantityBefore(input.quantityBefore());
        row.setQuantityAfter(input.quantityAfter());
        row.setAvailableBefore(input.availableBefore());
        row.setAvailableAfter(input.availableAfter());
        row.setSourceType(input.sourceType());
        row.setSourceId(input.sourceId());
        row.setReferenceActionId(input.referenceActionId());
        row.setExecutionId(input.executionId());
        row.setOperatorUserId(input.operatorUserId());
        row.setOccurredAt(input.occurredAt());
        row.setRecordedAt(input.recordedAt());
        row.setReason(input.reason());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmStockActionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmStockActionEntity updateById(String id, WmStockActionUpdateCommand input) {
        WmStockActionEntity row = requireRecord(id);
        row.setId(id);
        row.setRequestHash(input.requestHash());
        row.setStockId(input.stockId());
        row.setMaterialId(input.materialId());
        row.setUnitId(input.unitId());
        row.setWarehouseId(input.warehouseId());
        row.setBatchNo(input.batchNo());
        row.setBinCode(input.binCode());
        row.setActionType(input.actionType());
        row.setQuantityDelta(input.quantityDelta());
        row.setAvailableDelta(input.availableDelta());
        row.setQuantityBefore(input.quantityBefore());
        row.setQuantityAfter(input.quantityAfter());
        row.setAvailableBefore(input.availableBefore());
        row.setAvailableAfter(input.availableAfter());
        row.setSourceType(input.sourceType());
        row.setSourceId(input.sourceId());
        row.setReferenceActionId(input.referenceActionId());
        row.setExecutionId(input.executionId());
        row.setOperatorUserId(input.operatorUserId());
        row.setOccurredAt(input.occurredAt());
        row.setRecordedAt(input.recordedAt());
        row.setReason(input.reason());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmStockActionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        WmStockActionExample example = new WmStockActionExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private WmStockActionEntity requireRecord(String id) {
        WmStockActionEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

