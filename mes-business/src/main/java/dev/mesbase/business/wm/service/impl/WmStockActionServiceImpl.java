package dev.mesbase.business.wm.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.wm.domain.WmStockActionPage;
import dev.mesbase.business.wm.domain.WmStockActionQueryDto;
import dev.mesbase.business.wm.domain.WmStockActionResource;
import dev.mesbase.business.wm.service.IWmStockActionService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地仓储不可变收发存动作事实只读查询：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
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
    public WmStockActionPage selectPage(WmStockActionQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        WmStockActionExample example = new WmStockActionExample();
        WmStockActionExample.Criteria criteria = example.createCriteria();
        if (query.requestHash() != null) {
            criteria.andRequestHashLike("%" + query.requestHash() + "%");
        }
        if (query.stockId() != null) {
            criteria.andStockIdEqualTo(query.stockId());
        }
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo(query.unitId());
        }
        if (query.warehouseId() != null) {
            criteria.andWarehouseIdEqualTo(query.warehouseId());
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
            criteria.andQuantityDeltaEqualTo(query.quantityDelta());
        }
        if (query.availableDelta() != null) {
            criteria.andAvailableDeltaEqualTo(query.availableDelta());
        }
        if (query.quantityBefore() != null) {
            criteria.andQuantityBeforeEqualTo(query.quantityBefore());
        }
        if (query.quantityAfter() != null) {
            criteria.andQuantityAfterEqualTo(query.quantityAfter());
        }
        if (query.availableBefore() != null) {
            criteria.andAvailableBeforeEqualTo(query.availableBefore());
        }
        if (query.availableAfter() != null) {
            criteria.andAvailableAfterEqualTo(query.availableAfter());
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
            criteria.andExecutionIdEqualTo(query.executionId());
        }
        if (query.operatorUserId() != null) {
            criteria.andOperatorUserIdEqualTo(query.operatorUserId());
        }
        if (query.occurredAt() != null) {
            criteria.andOccurredAtEqualTo(query.occurredAt());
        }
        if (query.recordedAt() != null) {
            criteria.andRecordedAtEqualTo(query.recordedAt());
        }
        if (query.reason() != null) {
            criteria.andReasonLike("%" + query.reason() + "%");
        }
        example.setOrderByClause("recorded_at DESC,id DESC");
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

    private WmStockActionEntity requireRecord(String id) {
        WmStockActionEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

