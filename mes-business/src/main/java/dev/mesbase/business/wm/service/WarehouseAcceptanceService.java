package dev.mesbase.business.wm.service;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.common.core.page.PageData;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.MesWarehouseAcceptanceMapper;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceExample;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Immutable history only; new inbound postings belong to StockDocumentService.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WarehouseAcceptanceService {
    private final MesWarehouseAcceptanceMapper acceptances;

    public WarehouseAcceptanceService(MesWarehouseAcceptanceMapper acceptances) {
        this.acceptances = acceptances;
    }



    public record Page(List<MesWarehouseAcceptanceEntity> items, long total, int offset, int limit) implements PageData<MesWarehouseAcceptanceEntity> { }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Page history(String receiptId, int offset, int limit) {
        ProductionClosureRules.uuid(receiptId);
        ParameterChecks.paging(offset, limit);
        var f = new MesWarehouseAcceptanceExample();
        f.createCriteria().andReceiptIdEqualTo(receiptId);
        f.setOrderByClause("recorded_at DESC,id DESC");
        try (var p = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(acceptances.selectByExample(f));
            return new Page(rows, p.getTotal(), offset, limit);
        }
    }

}
