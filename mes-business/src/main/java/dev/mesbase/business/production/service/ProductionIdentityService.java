package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.domain.ProductionQrCode;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only business-key lookup; scanning creates no permanent identity or production fact.
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
        var execution = executions.selectByCode(ProductionQrCode.normalize(code));
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        return identity(execution);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ProductionIdentity read(String executionCode) {
        if (executionCode == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var execution = executions.selectByCode(executionCode);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        return identity(execution);
    }

    private ProductionIdentity identity(MesProductionExecutionEntity execution) {
        var workorder = workorders.selectByPrimaryKey(execution.getWorkorderId());
        var product = products.selectByPrimaryKey(execution.getProductId());
        RoutingEntity routing = null;
        if (execution.getRoutingId() != null) routing = routings.selectByPrimaryKey(execution.getRoutingId());
        if (workorder == null || product == null
                || execution.getRoutingId() != null && (routing == null || routing.getPublishedAt() == null)
                || !Objects.equals(workorder.getProductId(), execution.getProductId()))
            throw new FoundationException(ErrorCode.EXECUTION_INVALID);
        String outputCode = execution.getExecutionCode();
        Long routingId = null;
        String routingCode = null;
        Integer routingVersion = null;
        if (routing != null) {
            routingId = routing.getId();
            routingCode = routing.getCode();
            routingVersion = routing.getVersion();
        }
        return new ProductionIdentity(execution.getExecutionCode(), outputCode,
                workorder.getId(), workorder.getCode(), product.getId(), product.getCode(), product.getName(),
                routingId, routingCode, routingVersion, execution.getStatus(), execution.getTargetQty(),
                execution.getBatchNo());
    }

    /**
     * Batch is a persisted production property; no barcode mapping or historical reconstruction is consulted.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public String requiredBatch(String executionCode) {
        if (executionCode == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var execution = executions.selectByCode(executionCode);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        if (StringUtils.isBlank(execution.getBatchNo()))
            throw new FoundationException(ErrorCode.PRODUCTION_BATCH_REQUIRED);
        return execution.getBatchNo();
    }

}
