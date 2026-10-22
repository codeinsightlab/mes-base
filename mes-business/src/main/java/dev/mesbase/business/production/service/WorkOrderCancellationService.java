package dev.mesbase.business.production.service;

import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.business.production.dao.MesWorkorderCancellationMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesWorkorderCancellationEntity;
import dev.mesbase.business.production.domain.MesWorkorderCancellationExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops future work. Does not reverse or reinterpret any execution or warehouse fact.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCancellationService {
    public record Command(String requestId, String reason, Integer expectedRevision) {
    }

    private final MesWorkorderCancellationMapper cancellations;
    private final WorkorderMapper orders;
    private final ProductionChainLocks locks;
    private final BusinessSourceRules sources;

    public WorkOrderCancellationService(MesWorkorderCancellationMapper cancellations, WorkorderMapper orders,
                                        ProductionChainLocks locks, BusinessSourceRules sources) {
        this.cancellations = cancellations;
        this.orders = orders;
        this.locks = locks;
        this.sources = sources;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesWorkorderCancellationEntity history(Long id) {
        if (orders.selectByPrimaryKey(id) == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var filter = new MesWorkorderCancellationExample();
        filter.createCriteria().andWorkorderIdEqualTo(id);
        try (var page = PageHelper.offsetPage(0, 1, false)) {
            var rows = cancellations.selectByExample(filter);
            return rows.isEmpty() ? null : rows.getFirst();
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MesWorkorderCancellationEntity cancel(Long id, Command command) {
        if (command == null) ProductionClosureRules.bad();
        ProductionClosureRules.uuid(command.requestId());
        ProductionClosureRules.reason(command.reason());
        if (command.expectedRevision() != null && command.expectedRevision() < 0) ProductionClosureRules.bad();
        var actor = ProductionClosureRules.actor();
        var row = locks.order(id);
        String hash = ProductionClosureRules.hash(id, command.requestId(), command.reason(), command.expectedRevision(), actor);
        var previous = cancellations.selectByPrimaryKey(command.requestId());
        if (previous != null) {
            if (!hash.equals(previous.getRequestFingerprint())) ParameterChecks.reject(ErrorCode.WORKORDER_CANCELLATION_CONFLICT);
            return previous;
        }
        if (WorkOrderStatus.from(row.getStatus()) != WorkOrderStatus.DRAFT && WorkOrderStatus.from(row.getStatus()) != WorkOrderStatus.RELEASED)
            ParameterChecks.reject(ErrorCode.WORKORDER_CANCELLATION_CONFLICT);
        if (row.getSource() != null && !BusinessSource.LOCAL.getValue().equals(row.getSource()))
            throw new FoundationException(ErrorCode.EXTERNAL_SOURCE_READ_ONLY);
        // NULL is allowed only here to terminate future local work, never to create or reclassify facts.
        sources.localCreation();
        if (command.expectedRevision() != null && command.expectedRevision() != row.getRevision().intValue())
            ParameterChecks.reject(ErrorCode.WORKORDER_CANCELLATION_CONFLICT);
        var fact = new MesWorkorderCancellationEntity();
        fact.setId(command.requestId());
        fact.setWorkorderId(id);
        fact.setRequestFingerprint(hash);
        fact.setReason(command.reason());
        fact.setOperatorUserId(actor);
        fact.setRecordedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        fact.setPreviousStatus(row.getStatus());
        fact.setSourceSnapshot(row.getSource());
        fact.setPreviousRevision(row.getRevision());
        try {
            if (cancellations.insert(fact) != 1) ParameterChecks.reject(ErrorCode.WORKORDER_CANCELLATION_CONFLICT);
        } catch (DuplicateKeyException duplicate) {
            throw new FoundationException(ErrorCode.WORKORDER_CANCELLATION_CONFLICT, duplicate);
        }
        var filter = new WorkorderExample();
        filter.createCriteria().andIdEqualTo(id).andRevisionEqualTo(row.getRevision())
                .andStatusEqualTo(row.getStatus());
        var patch = new WorkorderEntity();
        patch.setStatus(WorkOrderStatus.CANCELLED.getValue());
        patch.setRevision(row.getRevision() + 1);
        patch.setUpdateTime(fact.getRecordedAt());
        if (orders.updateByExampleSelective(patch, filter) != 1) ParameterChecks.reject(ErrorCode.WORKORDER_CANCELLATION_CONFLICT);
        return fact;
    }

}
