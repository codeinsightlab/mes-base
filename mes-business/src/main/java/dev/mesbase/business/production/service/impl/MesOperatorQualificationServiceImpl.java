package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesOperatorQualificationMapper;
import dev.mesbase.business.production.dao.OperatorQualificationExtraMapper;
import dev.mesbase.business.production.domain.MesOperatorQualificationEntity;
import dev.mesbase.business.production.domain.MesOperatorQualificationExample;
import dev.mesbase.business.production.domain.OperatorQualificationSummary;
import dev.mesbase.business.production.domain.OperatorQualificationSummary.ExplicitOperation;
import dev.mesbase.business.production.service.IMesOperatorQualificationService;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesOperatorQualificationServiceImpl implements IMesOperatorQualificationService {
    private final MesOperatorQualificationMapper mapper;
    private final OperatorQualificationExtraMapper queries;
    private final OperationUserDirectory users;

    public MesOperatorQualificationServiceImpl(MesOperatorQualificationMapper mapper,
                                               OperatorQualificationExtraMapper queries, OperationUserDirectory users) {
        this.mapper = mapper;
        this.queries = queries;
        this.users = users;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true,
            isolation = Isolation.READ_COMMITTED)
    public OperatorQualificationSummary summary(Long userId, int offset, int limit) {
        requireUserId(userId);
        ParameterChecks.paging(offset, limit, 100);
        var setting = mapper.selectByPrimaryKey(userId);
        List<ExplicitOperation> rows;
        long total;
        try (Page<ExplicitOperation> page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(queries.explicitOperations(userId));
            total = page.getTotal();
        }
        boolean valid = !users.validUsers(List.of(userId)).isEmpty();
        return new OperatorQualificationSummary(userId,
                setting != null && Boolean.TRUE.equals(setting.getAllOperations()),
                setting == null ? 0 : setting.getRevision(), valid, total,
                queries.enabledExplicitCount(userId), rows, offset, limit);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void grantAll(Long userId, int revision) {
        requireUserId(userId);
        users.requireValid(userId);
        save(userId, revision, true);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void revokeAll(Long userId, int revision) {
        requireUserId(userId);
        // Revocation must remain possible for a disabled/deleted identity. Keep the row to prevent ABA.
        save(userId, revision, false);
    }

    private void save(Long userId, int revision, boolean all) {
        if (revision < 0 || revision == Integer.MAX_VALUE) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var now = LocalDateTime.now();
        var actor = SecurityUtils.getUserId();
        var row = new MesOperatorQualificationEntity();
        row.setUserId(userId);
        row.setAllOperations(all);
        row.setRevision(revision + 1);
        row.setUpdateBy(actor);
        row.setUpdateTime(now);
        if (revision == 0) {
            row.setCreateBy(actor);
            row.setCreateTime(now);
            try {
                if (mapper.insert(row) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            } catch (DuplicateKeyException collision) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, collision);
            }
        } else {
            var filter = new MesOperatorQualificationExample();
            filter.createCriteria().andUserIdEqualTo(userId).andRevisionEqualTo(revision);
            if (mapper.updateByExampleSelective(row, filter) != 1)
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private static void requireUserId(Long userId) {
        FactoryContext.required();
        if (userId == null || userId <= 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }
}
