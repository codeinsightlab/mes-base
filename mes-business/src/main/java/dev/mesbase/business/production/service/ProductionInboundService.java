package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and approves the same formal document used by the ordinary warehouse page.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInboundService {
    public record Command(@NotBlank String requestId, @NotNull Long stockId,
                          @NotNull @Positive BigDecimal quantity, @NotBlank @Size(max = 500) String reason) {
    }

    private final StockDocumentService documents;
    private final WmStockDocumentMapper headers;
    private final WmInventoryMapper inventory;
    private final MaterialMapper materials;
    private final ProductionCompletionService completion;
    private final ProductionChainLocks locks;
    private final AuthorizationService authorization;
    private final MaterialQuantityRules quantityRules;

    public ProductionInboundService(StockDocumentService documents, WmStockDocumentMapper headers,
                                    WmInventoryMapper inventory, MaterialMapper materials,
                                    ProductionCompletionService completion, ProductionChainLocks locks,
                                    AuthorizationService authorization, MaterialQuantityRules quantityRules) {
        this.documents = documents;
        this.headers = headers;
        this.inventory = inventory;
        this.materials = materials;
        this.completion = completion;
        this.locks = locks;
        this.authorization = authorization;
        this.quantityRules = quantityRules;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public StockDocumentModels.Detail receive(Long executionId, Command command) {
        permit("business:wm:stock:document:create");
        permit("business:wm:stock:document:post");
        if (executionId == null || command == null || command.stockId() == null || command.quantity() == null
                || command.quantity().signum() <= 0 || command.reason() == null || command.reason().isBlank()
                || command.reason().length() > 500 || command.requestId() == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        try {
            if (!UUID.fromString(command.requestId()).toString().equals(command.requestId()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
        // Same order/sibling -> document -> stock lock order as ordinary inbound approval.
        completion.lockInboundChain(executionId);
        var replay = replay(executionId, command);
        if (replay != null) return replay;
        var execution = locks.execution(executionId);
        var stock = inventory.selectByPrimaryKey(command.stockId());
        var material = materials.selectByPrimaryKey(execution.getProductId());
        if (stock == null || material == null || material.getUnitId() == null
                || !Objects.equals(execution.getProductId(), stock.getMaterialId())
                || execution.getBatchNo() == null || !execution.getBatchNo().equals(stock.getBatchNo()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        quantityRules.lockForWrite(List.of(material.getId()));
        // Document owner validates current rules or exact authoritative remaining production quantity.
        var draft = documents.create(new StockDocumentModels.Create(StockDocumentType.PRODUCTION_RECEIPT.getValue(),
                command.reason(), null, null, executionId));
        Long id = draft.document().getId();
        documents.putLine(id, null, new StockDocumentModels.Line(draft.document().getRevision(), command.stockId(),
                execution.getProductId(), stock.getWarehouseId(), material.getUnitId(), stock.getBatchNo(),
                stock.getBinCode(), command.quantity(), null));
        return documents.approve(id, new StockDocumentModels.Post(command.requestId(),
                documents.detail(id).document().getRevision()));
    }

    private StockDocumentModels.Detail replay(Long executionId, Command command) {
        var filter = new WmStockDocumentExample();
        filter.createCriteria().andPostRequestIdEqualTo(command.requestId());
        List<WmStockDocumentEntity> existing;
        try (var page = PageHelper.offsetPage(0, 2, false)) {
            existing = List.copyOf(headers.selectByExample(filter));
        }
        if (existing.isEmpty()) return null;
        if (existing.size() != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var header = existing.getFirst();
        if (!StockDocumentStatus.POSTED.getValue().equals(header.getStatus())
                || !StockDocumentType.PRODUCTION_RECEIPT.getValue().equals(header.getDocumentType())
                || !Objects.equals(executionId, header.getSourceExecutionId())
                || !Objects.equals(SecurityUtils.getUserId(), header.getPostedBy())
                || !Objects.equals(command.reason(), header.getReason()) || header.getRemark() != null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var detail = documents.detail(header.getId());
        if (detail.lines().size() != 1) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var line = detail.lines().getFirst();
        if (!Objects.equals(command.stockId(), line.getStockId())
                || command.quantity().compareTo(line.getQuantity()) != 0 || line.getStockActionId() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return detail;
    }

    private void permit(String permission) {
        if (!authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                FactoryContext.required(), new Permission(permission)))
            throw new FoundationException(ErrorCode.FORBIDDEN);
    }
}
