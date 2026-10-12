// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.pro.dao.ProWorkorderMapper;
import dev.mesbase.modules.pro.domain.ProWorkorderEntity;
import dev.mesbase.modules.pro.domain.ProWorkorderExample;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All source reads occur before this independent Spring-proxied write transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProWorkorderSyncWriter {
    private final ProWorkorderMapper mapper;
    private final Validator validator;

    public ProWorkorderSyncWriter(ProWorkorderMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public ProWorkorderSyncResult upsert(FactoryScope scope,
                                         List<ExternalProWorkorder> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.workorderCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new ProWorkorderSyncResult(0, 0, 0, 0);
        var example = new ProWorkorderExample();
        example.createCriteria().andWorkorderCodeIn(List.copyOf(keys));
        var existing = new HashMap<String, ProWorkorderEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getWorkorderCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.workorderCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new ProWorkorderEntity();
            }
            row.setWorkorderCode(input.workorderCode());
            row.setWorkorderName(input.workorderName());
            row.setOrderSource(input.orderSource());
            row.setSourceCode(input.sourceCode());
            row.setProductId(input.productId());
            row.setProductCode(input.productCode());
            row.setProductName(input.productName());
            row.setProductSpc(input.productSpc());
            row.setUnitOfMeasure(input.unitOfMeasure());
            row.setQuantity(input.quantity());
            row.setQuantityProduced(input.quantityProduced());
            row.setQuantityChanged(input.quantityChanged());
            row.setQuantityScheduled(input.quantityScheduled());
            row.setClientId(input.clientId());
            row.setClientCode(input.clientCode());
            row.setClientName(input.clientName());
            row.setBatchCode(input.batchCode());
            row.setRequestDate(input.requestDate());
            row.setParentId(input.parentId());
            row.setAncestors(input.ancestors());
            row.setFinishDate(input.finishDate());
            row.setStatus(input.status());
            row.setRemark(input.remark());
            row.setAttr1(input.attr1());
            row.setAttr2(input.attr2());
            row.setAttr3(input.attr3());
            row.setAttr4(input.attr4());
            row.setTransactionDate(input.transactionDate());
            row.setMoId(input.moId());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        return new ProWorkorderSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(ProWorkorderEntity row, ExternalProWorkorder input) {
        return true
                && Objects.deepEquals(row.getWorkorderCode(), input.workorderCode())
                && Objects.deepEquals(row.getWorkorderName(), input.workorderName())
                && Objects.deepEquals(row.getOrderSource(), input.orderSource())
                && Objects.deepEquals(row.getSourceCode(), input.sourceCode())
                && Objects.deepEquals(row.getProductId(), input.productId())
                && Objects.deepEquals(row.getProductCode(), input.productCode())
                && Objects.deepEquals(row.getProductName(), input.productName())
                && Objects.deepEquals(row.getProductSpc(), input.productSpc())
                && Objects.deepEquals(row.getUnitOfMeasure(), input.unitOfMeasure())
                && Objects.deepEquals(row.getQuantity(), input.quantity())
                && Objects.deepEquals(row.getQuantityProduced(), input.quantityProduced())
                && Objects.deepEquals(row.getQuantityChanged(), input.quantityChanged())
                && Objects.deepEquals(row.getQuantityScheduled(), input.quantityScheduled())
                && Objects.deepEquals(row.getClientId(), input.clientId())
                && Objects.deepEquals(row.getClientCode(), input.clientCode())
                && Objects.deepEquals(row.getClientName(), input.clientName())
                && Objects.deepEquals(row.getBatchCode(), input.batchCode())
                && Objects.deepEquals(row.getRequestDate(), input.requestDate())
                && Objects.deepEquals(row.getParentId(), input.parentId())
                && Objects.deepEquals(row.getAncestors(), input.ancestors())
                && Objects.deepEquals(row.getFinishDate(), input.finishDate())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark())
                && Objects.deepEquals(row.getAttr1(), input.attr1())
                && Objects.deepEquals(row.getAttr2(), input.attr2())
                && Objects.deepEquals(row.getAttr3(), input.attr3())
                && Objects.deepEquals(row.getAttr4(), input.attr4())
                && Objects.deepEquals(row.getTransactionDate(), input.transactionDate())
                && Objects.deepEquals(row.getMoId(), input.moId())
                ;
    }
}

