package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.BatchRouteNodeMapper;
import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.BatchRouteNodeEntity;
import dev.mesbase.business.production.domain.ProductionBatchEntity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retired ProductionBatch path interpretation, isolated from current production commands. */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class LegacyBatchHistoryQuery {
    private final ProductionBatchMapper batches;
    private final BatchRouteNodeMapper paths;
    private final WorkorderMapper orders;

    public LegacyBatchHistoryQuery(ProductionBatchMapper batches, BatchRouteNodeMapper paths, WorkorderMapper orders) {
        this.batches = batches;
        this.paths = paths;
        this.orders = orders;
    }

    public record Detail(ProductionBatchEntity batch, List<BatchRouteNodeEntity> path) {
    }

    public Detail detail(Long id) {
        var batch = batches.selectByPrimaryKey(id);
        if (batch == null || orders.selectByPrimaryKey(batch.getWorkorderId()) == null)
            throw new FoundationException(ErrorCode.NOT_FOUND);
        return new Detail(batch, paths.selectHistoryByBatchId(id));
    }
}
