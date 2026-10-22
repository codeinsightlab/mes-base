package dev.mesbase.business.production.service.impl;

import dev.mesbase.business.production.domain.MesOperationUserPage;
import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.domain.MesOperationUserCreateCommand;
import dev.mesbase.business.production.domain.MesOperationUserEntity;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.business.production.domain.MesOperationUserQueryDto;
import dev.mesbase.business.production.domain.MesOperationUserUpdateCommand;
import dev.mesbase.business.production.service.IMesOperationUserService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工序能力人员 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesOperationUserServiceImpl implements IMesOperationUserService {
    private final MesOperationUserMapper mapper;
    private final OperatorQualificationRules rules;

    public MesOperationUserServiceImpl(MesOperationUserMapper mapper, OperatorQualificationRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesOperationUserPage selectPage(MesOperationUserQueryDto query) {
        FactoryContext.required();
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesOperationUserExample example = new MesOperationUserExample();
        MesOperationUserExample.Criteria criteria = example.createCriteria();
        if (query.operationId() != null) {
            criteria.andOperationIdEqualTo(query.operationId());
        }
        if (query.userId() != null) {
            criteria.andUserIdEqualTo(query.userId());
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<MesOperationUserEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesOperationUserEntity> rows = mapper.selectByExample(example);
            return new MesOperationUserPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesOperationUserEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesOperationUserEntity insert(MesOperationUserCreateCommand input) {
        rules.capability(input.operationId(), input.userId());
        return insertValidated(input);
    }

    private MesOperationUserEntity insertValidated(MesOperationUserCreateCommand input) {
        MesOperationUserEntity row = new MesOperationUserEntity();
        row.setOperationId(input.operationId());
        row.setUserId(input.userId());
        row.setRemark(input.remark());
        row.setCreateBy(SecurityUtils.getUserId().toString());
        row.setCreateTime(LocalDateTime.now());
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesOperationUserEntity updateById(Long id, MesOperationUserUpdateCommand input) {
        MesOperationUserEntity row = requireRecord(id);
        if (!Objects.equals(row.getOperationId(), input.operationId()) || !Objects.equals(row.getUserId(), input.userId()))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        rules.capability(input.operationId(), input.userId());
        row.setId(id);
        row.setOperationId(input.operationId());
        row.setUserId(input.userId());
        row.setRemark(input.remark());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MesOperationUserExample example = new MesOperationUserExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void bindUsers(Long operationId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty() || userIds.size() > 100 || userIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(userIds).size() != userIds.size())
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        // At most 100 writes, references read in bulk; duplicate anywhere rolls back the complete addition.
        rules.capabilities(operationId, userIds);
        for (var id : userIds) insertValidated(new MesOperationUserCreateCommand(operationId, id, null));
    }

    private int insertUnique(MesOperationUserEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private MesOperationUserEntity requireRecord(Long id) {
        FactoryContext.required();
        MesOperationUserEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

