package dev.mesbase.business.production.service;

import dev.mesbase.common.utils.RequestFingerprint;

import dev.mesbase.common.validation.ParameterChecks;

import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.shared.enums.BusinessSource;

import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.business.integration.production.ProductionSupply.Requirement;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.production.ProductionErpAdapter;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.production.domain.ProductionCodeRules;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;

import java.math.BigDecimal;

import tools.jackson.databind.ObjectMapper;

import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInputSupplyService {
    private final BusinessSourceRules sources;
    private final ProductionIdentityService identities;
    private final WarehouseIntegration integration;
    private final List<ProductionErpAdapter> adapters;
    private final LocalProductionSupply local;
    private final TransactionTemplate readTransaction;
    private final MaterialQuantityRules quantityRules;

    public ProductionInputSupplyService(WarehouseIntegration integration, List<ProductionErpAdapter> adapters,
                                        LocalProductionSupply local,
                                        @Qualifier("businessTransactionManager") PlatformTransactionManager manager,
                                        BusinessSourceRules sources, ProductionIdentityService identities,
                                        MaterialQuantityRules quantityRules) {
        this.identities = identities;
        this.sources = sources;
        this.integration = integration;
        this.adapters = List.copyOf(adapters);
        this.local = local;
        this.quantityRules = quantityRules;
        readTransaction = new TransactionTemplate(manager);
        readTransaction.setReadOnly(true);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Context query(String code) {
        return query(code, null, 0, 20);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Context query(String code, Long workorderId, int offset, int limit) {
        ParameterChecks.paging(offset, limit);
        var identity = ProductionCodeRules.parse(code);
        var sourceOrder = readTransaction.execute(status -> identities.requireInputOrder(identity));
        if (workorderId != null && !workorderId.equals(sourceOrder.getId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        readTransaction.executeWithoutResult(status -> sources.order(sourceOrder.getId()));
        var scope = FactoryContext.required();
        var selection = integration.submissionSelection(scope);
        if (selection.mode() == Mode.CONNECTED) {
            var matching = adapters.stream().filter(a -> a.supports(scope, selection.bindingKey())).toList();
            if (matching.size() != 1) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            var result = matching.getFirst()
                    .query(scope, selection.bindingKey(), identity.workorderCode(), identity.productCode(), identity.batchNo());
            if (result == null || !Mode.CONNECTED.getValue().equals(result.sourceMode()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            readTransaction.executeWithoutResult(status -> sources.externalCandidates(result));
            var eligible = result.orders().stream()
                    .filter(o -> sourceOrder.getId().equals(o.workorderId())
                            && identity.workorderCode().equals(o.workorderCode())
                            && o.requirements().stream().anyMatch(r -> identity.productCode().equals(r.materialCode())))
                    .map(o -> new Order(o.workorderId(), o.workorderCode(), o.productCode(), o.productName(),
                            o.workCenter(), o.plannedQty(), o.outputUnitCode(), o.status(), o.allowedWarehouses(),
                            o.requirements(), o.sourceReference(), o.observedAt(), sourceOrder.getProductId()))
                    .toList();
            if (eligible.size() > 1) throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_AMBIGUOUS);
            var stocks = result.stocks().stream().filter(stock -> identity.batchNo().equals(stock.lotCode())
                    && eligible.stream().anyMatch(order -> order.allowedWarehouses().contains(stock.warehouseId())
                    && order.requirements().stream()
                    .anyMatch(requirement -> requirement.materialCode().equals(stock.materialCode())
                            && Objects.equals(requirement.unitId(), stock.unitId())))).toList();
            return new Context(result.sourceMode(), stocks, eligible.stream().skip(offset).limit(limit)
                    .toList(), eligible.size(), offset, limit);
        }
        return readTransaction.execute(status -> local.query(identity, sourceOrder, scope, selection, offset, limit));
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Calculation calculate(String code, Long orderId, String stockReference, BigDecimal outputQty) {
        var sourceOrder = readTransaction.execute(status -> {
            var order = sources.order(orderId);
            quantityRules.checkForMaterial(outputQty, order.getProductId());
            return order;
        });
        if (BusinessSource.LOCAL.getValue().equals(sourceOrder.getSource()))
            readTransaction.executeWithoutResult(status -> local.validateRequestedSource(orderId, stockReference));
        var context = query(code, orderId, 0, 1);
        var order = context.orders().stream().filter(o -> o.workorderId().equals(orderId)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.BAD_REQUEST));
        var stock = context.stocks().stream().filter(s -> s.reference().equals(stockReference)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.BAD_REQUEST));
        readTransaction.executeWithoutResult(status -> sources.warehouse(sourceOrder.getSource(), stock.warehouseId()));
        var required = order.requirements().stream()
                .filter(r -> r.totalRequiredQty() != null && r.totalRequiredQty().signum() > 0).toList();
        if (required.stream().map(Requirement::materialCode).distinct().count() > 1)
            throw new FoundationException(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED);
        if (required.isEmpty() || required.stream().map(Requirement::materialCode).distinct().count() != 1
                || required.stream().anyMatch(r -> !r.materialCode()
                .equals(stock.materialCode()) || !Objects.equals(r.unitId(), stock.unitId()) || !Objects.equals(r.unitCode(), stock.unitCode()))
                || !order.allowedWarehouses().contains(stock.warehouseId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var total = required.stream().map(Requirement::totalRequiredQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        Calculation calculation;
        if (Mode.LOCAL_ONLY.getValue().equals(context.sourceMode()))
            calculation = readTransaction.execute(status -> local.calculate(context.sourceMode(), order, stock, outputQty));
        else
            calculation = new Calculation(context.sourceMode(), order, stock, outputQty, issueQuantity(order.plannedQty(), total, outputQty));
        if (calculation.issueQty().compareTo(stock.availableQty()) > 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return calculation;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void lockQuantityMaterials(Calculation calculation) {
        var order = sources.order(calculation.order().workorderId());
        quantityRules.lockForWrite(List.of(order.getProductId(), calculation.stock().materialId()));
    }

    public void validateOutputQuantity(BigDecimal outputQty, Long orderId) {
        readTransaction.executeWithoutResult(status -> {
            var order = sources.order(orderId);
            quantityRules.checkForMaterial(outputQty, order.getProductId());
        });
    }

    /**
     * Common order lock is already held; all LOCAL reads participate in its transaction.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public Calculation calculateLocal(String code, Long orderId, String stockReference, BigDecimal outputQty) {
        var order = sources.order(orderId);
        if (!BusinessSource.LOCAL.getValue().equals(order.getSource()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        return calculate(code, orderId, stockReference, outputQty);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void validateFrozenCalculation(Calculation calculation) {
        var order = sources.order(calculation.order().workorderId());
        sources.warehouse(order.getSource(), calculation.stock().warehouseId());
        if (Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode())) local.validateFrozenCalculation(calculation);
    }

    public static String calculationKey(Calculation c) {
        var s = c.stock();
        var o = c.order();
        return RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(List.of(
                c.sourceMode(), o.workorderId(), o.productCode(), o.workCenter(), o.plannedQty().stripTrailingZeros()
                        .toPlainString(),
                o.requirements(), o.sourceReference(), s.reference(), s.materialCode(), s.lotCode(), s.warehouseId(), s.binCode() == null ? "" : s.binCode(), s.unitId(), s.unitCode(),
                c.outputQty().stripTrailingZeros().toPlainString(), c.issueQty()
                        .toPlainString(), c.basis() == null ? "" : c.basis())));
    }

    public static BigDecimal issueQuantity(BigDecimal plan, BigDecimal total, BigDecimal output) {
        if (plan == null || total == null || output == null || plan.signum() <= 0 || total.signum() <= 0 || output.signum() <= 0 || output.scale() > 6 || output.precision() - output.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var result = total.multiply(output).divide(plan, 6, RoundingMode.HALF_UP);
        if (result.signum() <= 0 || result.precision() - result.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return result;
    }

}
