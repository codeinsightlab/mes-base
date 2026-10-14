package dev.mesbase.business.issue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.issue.dao.MesMaterialIssueErpExtraMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraResource;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraUpdateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueErpExtraService;
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
 * 领料ERP外部事实 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesMaterialIssueErpExtraServiceImpl implements IMesMaterialIssueErpExtraService {
    private final MesMaterialIssueErpExtraMapper mapper;

    public MesMaterialIssueErpExtraServiceImpl(MesMaterialIssueErpExtraMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueErpExtraResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesMaterialIssueErpExtraPage selectPage(MesMaterialIssueErpExtraQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesMaterialIssueErpExtraExample example = new MesMaterialIssueErpExtraExample();
        MesMaterialIssueErpExtraExample.Criteria criteria = example.createCriteria();
        if (query.sourceExecutionMode() != null) {
            criteria.andSourceExecutionModeLike("%" + query.sourceExecutionMode() + "%");
        }
        if (query.externalEffect() != null) {
            criteria.andExternalEffectLike("%" + query.externalEffect() + "%");
        }
        if (query.externalBindingKey() != null) {
            criteria.andExternalBindingKeyLike("%" + query.externalBindingKey() + "%");
        }
        if (query.externalReference() != null) {
            criteria.andExternalReferenceLike("%" + query.externalReference() + "%");
        }
        if (query.voidResultStatus() != null) {
            criteria.andVoidResultStatusLike("%" + query.voidResultStatus() + "%");
        }
        if (query.voidExecutionMode() != null) {
            criteria.andVoidExecutionModeLike("%" + query.voidExecutionMode() + "%");
        }
        if (query.voidMessage() != null) {
            criteria.andVoidMessageLike("%" + query.voidMessage() + "%");
        }
        if (query.provider() != null) {
            criteria.andProviderLike("%" + query.provider() + "%");
        }
        if (query.externalCode() != null) {
            criteria.andExternalCodeLike("%" + query.externalCode() + "%");
        }
        if (query.documentStatus() != null) {
            criteria.andDocumentStatusLike("%" + query.documentStatus() + "%");
        }
        if (query.lastOperation() != null) {
            criteria.andLastOperationLike("%" + query.lastOperation() + "%");
        }
        if (query.lastOutcome() != null) {
            criteria.andLastOutcomeLike("%" + query.lastOutcome() + "%");
        }
        if (query.lastMessage() != null) {
            criteria.andLastMessageLike("%" + query.lastMessage() + "%");
        }
        if (query.operationVersion() != null) {
            criteria.andOperationVersionEqualTo(query.operationVersion());
        }
        if (query.checkedAt() != null) {
            criteria.andCheckedAtEqualTo(query.checkedAt());
        }
        example.setOrderByClause("id");
        try (Page<MesMaterialIssueErpExtraEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesMaterialIssueErpExtraEntity> rows = mapper.selectByExample(example);
            return new MesMaterialIssueErpExtraPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueErpExtraResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesMaterialIssueErpExtraEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueErpExtraResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesMaterialIssueErpExtraEntity insert(MesMaterialIssueErpExtraCreateCommand input) {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueErpExtraResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueErpExtraEntity updateById(Long id, MesMaterialIssueErpExtraUpdateCommand input) {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueErpExtraResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private MesMaterialIssueErpExtraEntity requireRecord(Long id) {
        MesMaterialIssueErpExtraEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

