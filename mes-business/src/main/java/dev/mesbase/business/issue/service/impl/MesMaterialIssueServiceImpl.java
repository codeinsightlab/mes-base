package dev.mesbase.business.issue.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssuePage;
import dev.mesbase.business.issue.domain.MesMaterialIssueQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueResource;
import dev.mesbase.business.issue.domain.MesMaterialIssueUpdateCommand;
import dev.mesbase.business.issue.service.IMesMaterialIssueService;
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
 * 领料出库单 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesMaterialIssueServiceImpl implements IMesMaterialIssueService {
    private final MesMaterialIssueMapper mapper;
    private final MaterialIssueRules rules;

    public MesMaterialIssueServiceImpl(MesMaterialIssueMapper mapper, MaterialIssueRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesMaterialIssuePage selectPage(MesMaterialIssueQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesMaterialIssueExample example = new MesMaterialIssueExample();
        MesMaterialIssueExample.Criteria criteria = example.createCriteria();
        if (query.issueNo() != null) {
            criteria.andIssueNoLike("%" + query.issueNo() + "%");
        }
        if (query.workorderId() != null) {
            criteria.andWorkorderIdEqualTo(query.workorderId());
        }
        if (query.workorderCode() != null) {
            criteria.andWorkorderCodeLike("%" + query.workorderCode() + "%");
        }
        if (query.productId() != null) {
            criteria.andProductIdEqualTo(query.productId());
        }
        if (query.productCode() != null) {
            criteria.andProductCodeLike("%" + query.productCode() + "%");
        }
        if (query.flowCardNo() != null) {
            criteria.andFlowCardNoLike("%" + query.flowCardNo() + "%");
        }
        if (query.routingId() != null) {
            criteria.andRoutingIdEqualTo(query.routingId());
        }
        if (query.issueTime() != null) {
            criteria.andIssueTimeEqualTo(query.issueTime());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id");
        try (Page<MesMaterialIssueEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesMaterialIssueEntity> rows = mapper.selectByExample(example);
            return new MesMaterialIssuePage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesMaterialIssueResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesMaterialIssueEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public MesMaterialIssueEntity insert(MesMaterialIssueCreateCommand input) {
        MesMaterialIssueEntity row = new MesMaterialIssueEntity();

        row.setIssueNo(input.issueNo());
        row.setWorkorderId(input.workorderId());
        row.setWorkorderCode(input.workorderCode());
        row.setProductId(input.productId());
        row.setProductCode(input.productCode());
        row.setFlowCardNo(input.flowCardNo());
        row.setRoutingId(input.routingId());
        row.setIssueTime(input.issueTime());
        row.setStatus(input.status());
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
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesMaterialIssueEntity updateById(Long id, MesMaterialIssueUpdateCommand input) {
        MesMaterialIssueEntity row = requireRecord(id);
        row.setId(id);
        row.setIssueNo(input.issueNo());
        row.setWorkorderId(input.workorderId());
        row.setWorkorderCode(input.workorderCode());
        row.setProductId(input.productId());
        row.setProductCode(input.productCode());
        row.setFlowCardNo(input.flowCardNo());
        row.setRoutingId(input.routingId());
        row.setIssueTime(input.issueTime());
        row.setStatus(input.status());
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
    @BusinessCacheEvict(business = MesMaterialIssueResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingIssues(ids);
        MesMaterialIssueExample example = new MesMaterialIssueExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)

        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MesMaterialIssueEntity requireRecord(Long id) {
        MesMaterialIssueEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

