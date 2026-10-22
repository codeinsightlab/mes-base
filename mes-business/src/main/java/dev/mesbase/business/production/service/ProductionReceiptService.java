package dev.mesbase.business.production.service;

import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.dao.ProductionReceiptQueryMapper;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Page;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Detail;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Query;
import dev.mesbase.business.production.domain.ProductionReceiptRow;
import dev.mesbase.business.production.domain.ProductionReceiptType;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only pre-convergence history. New inventory business uses stock documents.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReceiptService {
    private final MesProductionReceiptMapper receipts;
    private final ProductionReceiptQueryMapper queries;
    private final BusinessDirectoryService directory;

    public ProductionReceiptService(MesProductionReceiptMapper receipts, ProductionReceiptQueryMapper queries,
                                    BusinessDirectoryService directory) {
        this.receipts = receipts;
        this.queries = queries;
        this.directory = directory;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Page page(Query q) {
        ParameterChecks.paging(q.offset(), q.limit());
        if (q.productLotCode() != null && q.productLotCode().length() > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (q.referenceReceiptId() != null) uuid(q.referenceReceiptId());
        List<ProductionReceiptRow> rows;
        long total;
        try (var p = PageHelper.offsetPage(q.offset(), q.limit())) {
            rows = List.copyOf(queries.page(q));
            total = p.getTotal();
        }
        return new Page(rows, total, q.offset(), q.limit(), names(rows));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(String id) {
        uuid(id);
        var row = queries.detail(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return new Detail(row, receipts.originalBalance(ProductionReceiptType.RECEIPT.getValue()
                .equals(row.getRecordType()) ? id : row.getReferenceReceiptId()), names(List.of(row)));
    }

    private Map<Long, String> names(List<ProductionReceiptRow> rows) {
        return directory.userLabels(rows.stream().flatMap(r -> Stream.of(r.getOperatorUserId(), r.getRecordedBy()))
                        .distinct().toList()).values().stream()
                .filter(l -> l.name() != null).collect(Collectors.toMap(l -> l.id(), l -> l.name()));
    }

    private static void uuid(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

}
