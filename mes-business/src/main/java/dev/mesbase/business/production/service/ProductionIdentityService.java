package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.MesProductionScanIdentityExample;
import dev.mesbase.business.production.dao.MesProductionScanIdentityMapper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only identity lookup; scanner input never selects a workflow or creates facts.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionIdentityService {
    private final MesProductionExecutionMapper executions;
    private final WorkorderMapper workorders;
    private final MaterialMapper products;
    private final MesProductionScanIdentityMapper bindings;
    private final RoutingMapper routings;

    public ProductionIdentityService(MesProductionExecutionMapper executions, WorkorderMapper workorders,
                                     MaterialMapper products, RoutingMapper routings,
                                     MesProductionScanIdentityMapper bindings) {
        this.executions = executions;
        this.workorders = workorders;
        this.products = products;
        this.routings = routings;
        this.bindings = bindings;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ProductionIdentity resolve(String code) {
        Long id;
        if (code != null && code.startsWith("MES1.")) {
            var parsed = ProductionScanCode.parse(code);
            var binding = bindings.selectByPrimaryKey(parsed.key());
            if (binding == null || !parsed.encode().equals(binding.getEncodedCode()))
                throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
            id = binding.getExecutionId();
        } else if (code != null && code.contains("#")) {
            var parsed = ProductionScanCode.material(code);
            var filter = new MesProductionScanIdentityExample();
            filter.createCriteria().andMaterialCodeEqualTo(parsed.materialCode())
                    .andMaterialLotCodeEqualTo(parsed.lotCode());
            if (bindings.countByExample(filter) > 1)
                throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_AMBIGUOUS);
            var found = bindings.selectByExample(filter);
            if (found.isEmpty()) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
            if (found.size() != 1) throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_AMBIGUOUS);
            var canonical = ProductionScanCode.parse(found.getFirst().getEncodedCode());
            if (!canonical.materialCode().equals(parsed.materialCode()) || !canonical.lotCode()
                    .equals(parsed.lotCode())) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            code = canonical.encode();
            id = found.getFirst().getExecutionId();
        } else id = parse(code);
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var workorder = workorders.selectByPrimaryKey(execution.getWorkorderId());
        var product = products.selectByPrimaryKey(execution.getProductId());
        var routing = routings.selectByPrimaryKey(execution.getRoutingId());
        if (workorder == null || product == null || routing == null || routing.getPublishedAt() == null)
            throw new FoundationException(ErrorCode.EXECUTION_INVALID);
        return new ProductionIdentity(execution.getId(), execution.getExecutionNo(), code.startsWith("MES1.") ? code : "EXE:" + execution.getId(),
                workorder.getId(), workorder.getCode(), product.getId(), product.getCode(), product.getName(),
                routing.getId(), routing.getCode(), routing.getVersion(), execution.getStatus(), execution.getTargetQty());
    }

    public static Long parse(String code) {
        if (code == null || !code.matches("EXE:[1-9][0-9]{0,18}"))
            throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID);
        try {
            return Long.valueOf(code.substring(4));
        } catch (NumberFormatException failure) {
            throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID, failure);
        }
    }
}
