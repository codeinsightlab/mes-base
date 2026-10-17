package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import java.util.List;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ExecutionQualityReferenceQueryImpl implements ExecutionQualityReferenceQuery, ProductionDefectQuantityQuery {
    private final MesProductionDefectMapper mapper;
    private final QualityTreatmentService treatments;

    public ExecutionQualityReferenceQueryImpl(MesProductionDefectMapper mapper, QualityTreatmentService treatments) {
        this.mapper = mapper;
        this.treatments = treatments;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public boolean hasAnyHistory(Long executionId) {
        if (executionId == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return mapper.existsExecutionHistory(executionId);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<NodeTotal> nodeTotals(Long executionId) {
        if (executionId == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return treatments.nodeTotals(executionId);
    }
}
