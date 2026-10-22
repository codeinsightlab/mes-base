package dev.mesbase.business.quality.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.quality.dao.MesQualityReleaseMapper;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleaseExample;
import dev.mesbase.business.quality.domain.MesQualityReleasePage;
import dev.mesbase.business.quality.domain.MesQualityReleaseQueryDto;
import dev.mesbase.business.quality.domain.MesQualityReleaseResource;
import dev.mesbase.business.quality.service.IMesQualityReleaseService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 正式质量放行事实 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesQualityReleaseServiceImpl implements IMesQualityReleaseService {
    private final MesQualityReleaseMapper mapper;

    public MesQualityReleaseServiceImpl(MesQualityReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesQualityReleaseResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesQualityReleasePage selectPage(MesQualityReleaseQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesQualityReleaseExample example = new MesQualityReleaseExample();
        MesQualityReleaseExample.Criteria criteria = example.createCriteria();
        if (query.executionId() != null) {
            criteria.andExecutionIdEqualTo(query.executionId());
        }
        if (query.sequenceNo() != null) {
            criteria.andSequenceNoEqualTo(query.sequenceNo());
        }
        if (query.actionType() != null) {
            criteria.andActionTypeLike("%" + query.actionType() + "%");
        }
        if (query.quantity() != null) {
            criteria.andQuantityEqualTo(query.quantity());
        }
        if (query.basisHash() != null) {
            criteria.andBasisHashLike("%" + query.basisHash() + "%");
        }
        if (query.referenceId() != null) {
            criteria.andReferenceIdLike("%" + query.referenceId() + "%");
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
        try (Page<MesQualityReleaseEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesQualityReleaseEntity> rows = mapper.selectByExample(example);
            return new MesQualityReleasePage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesQualityReleaseResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesQualityReleaseEntity selectById(String id) {
        return requireRecord(id);
    }


    private MesQualityReleaseEntity requireRecord(String id) {
        MesQualityReleaseEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

