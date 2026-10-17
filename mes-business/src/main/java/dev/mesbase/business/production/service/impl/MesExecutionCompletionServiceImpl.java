package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesExecutionCompletionMapper;
import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionExample;
import dev.mesbase.business.production.domain.MesExecutionCompletionPage;
import dev.mesbase.business.production.domain.MesExecutionCompletionQueryDto;
import dev.mesbase.business.production.domain.MesExecutionCompletionResource;
import dev.mesbase.business.production.service.IMesExecutionCompletionService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 正式生产完工事实 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesExecutionCompletionServiceImpl implements IMesExecutionCompletionService {
    private final MesExecutionCompletionMapper mapper;

    public MesExecutionCompletionServiceImpl(MesExecutionCompletionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesExecutionCompletionResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesExecutionCompletionPage selectPage(MesExecutionCompletionQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesExecutionCompletionExample example = new MesExecutionCompletionExample();
        MesExecutionCompletionExample.Criteria criteria = example.createCriteria();
        if (query.executionId() != null) {
            criteria.andExecutionIdEqualTo(query.executionId());
        }
        if (query.effectiveQty() != null) {
            criteria.andEffectiveQtyEqualTo(query.effectiveQty());
        }
        if (query.releasedQty() != null) {
            criteria.andReleasedQtyEqualTo(query.releasedQty());
        }
        if (query.receiptQty() != null) {
            criteria.andReceiptQtyEqualTo(query.receiptQty());
        }
        if (query.acceptedQty() != null) {
            criteria.andAcceptedQtyEqualTo(query.acceptedQty());
        }
        if (query.basisHash() != null) {
            criteria.andBasisHashLike("%" + query.basisHash() + "%");
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
        try (Page<MesExecutionCompletionEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesExecutionCompletionEntity> rows = mapper.selectByExample(example);
            return new MesExecutionCompletionPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesExecutionCompletionResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesExecutionCompletionEntity selectById(String id) {
        return requireRecord(id);
    }


    private MesExecutionCompletionEntity requireRecord(String id) {
        MesExecutionCompletionEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

