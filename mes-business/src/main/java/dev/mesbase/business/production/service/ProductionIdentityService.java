package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
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
        MesProductionExecutionEntity execution;
        if (code != null && code.startsWith("EXE:") && code.indexOf('#') < 0) {
            execution = executions.selectByPrimaryKey(parse(code));
        } else {
            var scanned = ProductionScanCode.parse(code);
            var order = orderByExactCode(scanned.workorderCode());
            var product = products.selectByPrimaryKey(order.getProductId());
            if (product == null || !scanned.productCode().equals(product.getCode()))
                throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
            execution = findExecution(order.getId(), product.getId(), scanned.batchNo());
        }
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var workorder = workorders.selectByPrimaryKey(execution.getWorkorderId());
        var product = products.selectByPrimaryKey(execution.getProductId());
        var routing = routings.selectByPrimaryKey(execution.getRoutingId());
        if (workorder == null || product == null || routing == null || routing.getPublishedAt() == null
                || !Objects.equals(workorder.getProductId(), execution.getProductId()))
            throw new FoundationException(ErrorCode.EXECUTION_INVALID);
        String outputCode = "EXE:" + execution.getId();
        if (execution.getBatchNo() != null)
            outputCode = new ProductionScanCode(workorder.getCode(), product.getCode(), execution.getBatchNo()).encode();
        return new ProductionIdentity(execution.getId(), execution.getExecutionNo(), outputCode,
                workorder.getId(), workorder.getCode(), product.getId(), product.getCode(), product.getName(),
                routing.getId(), routing.getCode(), routing.getVersion(), execution.getStatus(), execution.getTargetQty(),
                execution.getBatchNo());
    }

    /**
     * Generated indexed predicates narrow the query; Java equality preserves exact UTF-8 text semantics even on
     * case-insensitive or PAD SPACE database collations. Never limit approximate candidates before exact comparison.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MesProductionExecutionEntity findExecution(Long workorderId, Long productId, String batchNo) {
        if (workorderId == null || productId == null || batchNo == null)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        MesProductionExecutionEntity match = null;
        long after = 0;
        while (true) {
            var filter = new MesProductionExecutionExample();
            filter.createCriteria().andWorkorderIdEqualTo(workorderId).andProductIdEqualTo(productId)
                    .andBatchNoEqualTo(batchNo).andIdGreaterThan(after);
            filter.setOrderByClause("id ASC");
            List<MesProductionExecutionEntity> rows;
            try (var page = PageHelper.offsetPage(0, 100, false)) {
                rows = List.copyOf(executions.selectByExample(filter));
            }
            if (rows.isEmpty()) return match;
            for (var row : rows) {
                after = row.getId();
                if (!batchNo.equals(row.getBatchNo())) continue;
                if (match != null) throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_AMBIGUOUS);
                match = row;
            }
        }
    }

    private WorkorderEntity orderByExactCode(String code) {
        WorkorderEntity match = null;
        long after = 0;
        while (true) {
            var filter = new WorkorderExample();
            filter.createCriteria().andCodeEqualTo(code).andIdGreaterThan(after);
            filter.setOrderByClause("id ASC");
            List<WorkorderEntity> rows;
            try (var page = PageHelper.offsetPage(0, 100, false)) {
                rows = List.copyOf(workorders.selectByExample(filter));
            }
            if (rows.isEmpty()) {
                if (match == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
                return match;
            }
            for (var row : rows) {
                after = row.getId();
                if (!code.equals(row.getCode())) continue;
                if (match != null) throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_AMBIGUOUS);
                match = row;
            }
        }
    }

    /**
     * Batch is a persisted production property; no barcode mapping or historical reconstruction is consulted.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public String requiredBatch(Long executionId) {
        if (executionId == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var execution = executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        if (StringUtils.isBlank(execution.getBatchNo()))
            throw new FoundationException(ErrorCode.PRODUCTION_BATCH_REQUIRED);
        return execution.getBatchNo();
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
