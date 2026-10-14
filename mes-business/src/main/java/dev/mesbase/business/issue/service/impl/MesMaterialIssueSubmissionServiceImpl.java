package dev.mesbase.business.issue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.issue.dao.MesMaterialIssueSubmissionMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionResource;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionUpdateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueSubmissionService;
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
 * 领料正式提交尝试及冻结载荷历史 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesMaterialIssueSubmissionServiceImpl implements IMesMaterialIssueSubmissionService {
    private final MesMaterialIssueSubmissionMapper mapper;

    public MesMaterialIssueSubmissionServiceImpl(MesMaterialIssueSubmissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueSubmissionResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesMaterialIssueSubmissionPage selectPage(MesMaterialIssueSubmissionQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesMaterialIssueSubmissionExample example = new MesMaterialIssueSubmissionExample();
        MesMaterialIssueSubmissionExample.Criteria criteria = example.createCriteria();
        if (query.issueId() != null) {
            criteria.andIssueIdEqualTo(query.issueId());
        }
        if (query.pathRevision() != null) {
            criteria.andPathRevisionEqualTo(query.pathRevision());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.createdAt() != null) {
            criteria.andCreatedAtEqualTo(query.createdAt());
        }
        if (query.finishedAt() != null) {
            criteria.andFinishedAtEqualTo(query.finishedAt());
        }
        example.setOrderByClause("id");
        try (Page<MesMaterialIssueSubmissionEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesMaterialIssueSubmissionEntity> rows = mapper.selectByExampleWithBLOBs(example);
            return new MesMaterialIssueSubmissionPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueSubmissionResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesMaterialIssueSubmissionEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueSubmissionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesMaterialIssueSubmissionEntity insert(MesMaterialIssueSubmissionCreateCommand input) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueSubmissionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueSubmissionEntity updateById(Long id, MesMaterialIssueSubmissionUpdateCommand input) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueSubmissionResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    private MesMaterialIssueSubmissionEntity requireRecord(Long id) {
        MesMaterialIssueSubmissionEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

