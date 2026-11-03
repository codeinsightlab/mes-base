package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import java.util.List;
import java.util.Map;
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
    public boolean hasAnyHistory(String executionCode) {
        if (executionCode == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return mapper.existsExecutionHistory(executionCode);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<NodeTotal> nodeTotals(String executionCode) {
        if (executionCode == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return treatments.nodeTotals(executionCode);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Map<String, List<NodeTotal>> nodeTotalsBatch(List<String> executionCodes) {
        return treatments.nodeTotalsBatch(executionCodes);
    }
}
