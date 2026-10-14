package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ProductionIdentity;
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
    private final RoutingMapper routings;

    public ProductionIdentityService(MesProductionExecutionMapper executions, WorkorderMapper workorders,
                                     MaterialMapper products, RoutingMapper routings) {
        this.executions = executions;
        this.workorders = workorders;
        this.products = products;
        this.routings = routings;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ProductionIdentity resolve(String code) {
        Long id = parse(code);
        var execution = executions.selectByPrimaryKey(id);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var workorder = workorders.selectByPrimaryKey(execution.getWorkorderId());
        var product = products.selectByPrimaryKey(execution.getProductId());
        var routing = routings.selectByPrimaryKey(execution.getRoutingId());
        if (workorder == null || product == null || routing == null || routing.getPublishedAt() == null)
            throw new FoundationException(ErrorCode.EXECUTION_INVALID);
        return new ProductionIdentity(execution.getId(), execution.getExecutionNo(), "EXE:" + execution.getId(),
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
