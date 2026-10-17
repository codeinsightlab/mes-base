package dev.mesbase.business.wm.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.MesWarehouseAcceptanceMapper;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceExample;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptancePage;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceQueryDto;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceResource;
import dev.mesbase.business.wm.service.IMesWarehouseAcceptanceService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成品仓储接收事实 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesWarehouseAcceptanceServiceImpl implements IMesWarehouseAcceptanceService {
    private final MesWarehouseAcceptanceMapper mapper;

    public MesWarehouseAcceptanceServiceImpl(MesWarehouseAcceptanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesWarehouseAcceptanceResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesWarehouseAcceptancePage selectPage(MesWarehouseAcceptanceQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesWarehouseAcceptanceExample example = new MesWarehouseAcceptanceExample();
        MesWarehouseAcceptanceExample.Criteria criteria = example.createCriteria();
        if (query.receiptId() != null) {
            criteria.andReceiptIdLike("%" + query.receiptId() + "%");
        }
        if (query.executionId() != null) {
            criteria.andExecutionIdEqualTo(query.executionId());
        }
        if (query.productId() != null) {
            criteria.andProductIdEqualTo(query.productId());
        }
        if (query.stockId() != null) {
            criteria.andStockIdEqualTo(query.stockId());
        }
        if (query.quantityDelta() != null) {
            criteria.andQuantityDeltaEqualTo(query.quantityDelta());
        }
        if (query.referenceId() != null) {
            criteria.andReferenceIdLike("%" + query.referenceId() + "%");
        }
        if (query.stockActionId() != null) {
            criteria.andStockActionIdLike("%" + query.stockActionId() + "%");
        }
        if (query.requestHash() != null) {
            criteria.andRequestHashLike("%" + query.requestHash() + "%");
        }
        if (query.operatorUserId() != null) {
            criteria.andOperatorUserIdEqualTo(query.operatorUserId());
        }
        if (query.recordedAt() != null) {
            criteria.andRecordedAtEqualTo(query.recordedAt());
        }
        if (query.reason() != null) {
            criteria.andReasonLike("%" + query.reason() + "%");
        }
        example.setOrderByClause("id");
        try (Page<MesWarehouseAcceptanceEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesWarehouseAcceptanceEntity> rows = mapper.selectByExample(example);
            return new MesWarehouseAcceptancePage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesWarehouseAcceptanceResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesWarehouseAcceptanceEntity selectById(String id) {
        return requireRecord(id);
    }


    private MesWarehouseAcceptanceEntity requireRecord(String id) {
        MesWarehouseAcceptanceEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

