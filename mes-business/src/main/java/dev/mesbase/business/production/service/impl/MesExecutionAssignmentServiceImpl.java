package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.MesExecutionAssignmentCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionAssignmentEntity;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import dev.mesbase.business.production.domain.MesExecutionAssignmentPage;
import dev.mesbase.business.production.domain.MesExecutionAssignmentQueryDto;
import dev.mesbase.business.production.domain.MesExecutionAssignmentUpdateCommand;
import dev.mesbase.business.production.service.IMesExecutionAssignmentService;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产节点派工 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesExecutionAssignmentServiceImpl implements IMesExecutionAssignmentService {
    private final MesExecutionAssignmentMapper mapper;
    private final OperatorQualificationRules rules;

    public MesExecutionAssignmentServiceImpl(MesExecutionAssignmentMapper mapper, OperatorQualificationRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesExecutionAssignmentPage selectPage(MesExecutionAssignmentQueryDto query) {
        FactoryContext.required();
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesExecutionAssignmentExample example = new MesExecutionAssignmentExample();
        MesExecutionAssignmentExample.Criteria criteria = example.createCriteria();
        if (query.executionId() != null) {
            criteria.andExecutionIdEqualTo(query.executionId());
        }
        if (query.routingNodeId() != null) {
            criteria.andRoutingNodeIdEqualTo(query.routingNodeId());
        }
        if (query.userId() != null) {
            criteria.andUserIdEqualTo(query.userId());
        }
        if (query.assignedBy() != null) {
            criteria.andAssignedByEqualTo(query.assignedBy());
        }
        if (query.assignedAt() != null) {
            criteria.andAssignedAtEqualTo(query.assignedAt());
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<MesExecutionAssignmentEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesExecutionAssignmentEntity> rows = mapper.selectByExample(example);
            return new MesExecutionAssignmentPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesExecutionAssignmentEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesExecutionAssignmentEntity insert(MesExecutionAssignmentCreateCommand input) {
        var node = rules.node(input.executionId(), input.routingNodeId(), true);
        rules.requireCapability(node.getOperationId(), input.userId());
        MesExecutionAssignmentEntity row = new MesExecutionAssignmentEntity();
        row.setExecutionId(input.executionId());
        row.setRoutingNodeId(input.routingNodeId());
        row.setUserId(input.userId());
        row.setAssignedBy(SecurityUtils.getUserId());
        row.setAssignedAt(LocalDateTime.now());
        row.setRemark(input.remark());
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesExecutionAssignmentEntity updateById(Long id, MesExecutionAssignmentUpdateCommand input) {
        MesExecutionAssignmentEntity row = requireRecord(id);
        if (!Objects.equals(row.getExecutionId(), input.executionId()) || !Objects.equals(row.getRoutingNodeId(), input.routingNodeId()) || !Objects.equals(row.getUserId(), input.userId()))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var node = rules.node(row.getExecutionId(), row.getRoutingNodeId(), true);
        rules.requireCapability(node.getOperationId(), row.getUserId());
        row.setId(id);
        row.setExecutionId(input.executionId());
        row.setRoutingNodeId(input.routingNodeId());
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
        MesExecutionAssignmentExample example = new MesExecutionAssignmentExample();
        // At most 100 rows; acquire parent locks in a stable order before deleting.
        var rows = ids.stream().map(this::requireRecord)
                .sorted(Comparator.comparing(MesExecutionAssignmentEntity::getExecutionId)).toList();
        for (var row : rows) rules.cancelAssignment(row.getExecutionId());
        example.createCriteria().andIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(MesExecutionAssignmentEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private MesExecutionAssignmentEntity requireRecord(Long id) {
        FactoryContext.required();
        MesExecutionAssignmentEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

