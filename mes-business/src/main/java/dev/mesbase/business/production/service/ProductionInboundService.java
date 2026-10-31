package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AutoCodeNumbering autoCodes;
    private final TransactionTemplate businessTransactions;

    public ProductionInboundService(StockDocumentService documents, WmStockDocumentMapper headers,
                                    WmInventoryMapper inventory, MaterialMapper materials,
                                    ProductionCompletionService completion, ProductionChainLocks locks,
                                    AuthorizationService authorization, MaterialQuantityRules quantityRules,
                                    AutoCodeNumbering autoCodes,
                                    @Qualifier("businessTransactionManager") PlatformTransactionManager businessManager) {
        this.documents = documents;
        this.headers = headers;
        this.inventory = inventory;
        this.materials = materials;
        this.completion = completion;
        this.locks = locks;
        this.authorization = authorization;
        this.quantityRules = quantityRules;
        this.autoCodes = autoCodes;
        this.businessTransactions = new TransactionTemplate(businessManager);
        this.businessTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED,
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
        var replay = businessTransactions.execute(status -> replay(executionId, command));
        if (replay != null) return replay;
        String ruleCode = StockDocumentType.PRODUCTION_RECEIPT.getValue();
        String documentNo = autoCodes.getBusinessDocNo(ruleCode, AutoCodeRuleType.STOCK_IN.name());
        return businessTransactions.execute(status -> receiveInTransaction(executionId, command, documentNo));
    }

    private StockDocumentModels.Detail receiveInTransaction(Long executionId, Command command, String documentNo) {
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
        var draft = documents.createWithNumber(new StockDocumentModels.Create(
                StockDocumentDirection.IN.getValue(), StockDocumentType.PRODUCTION_RECEIPT.getValue(), command.reason(),
                null, null, executionId, StockDocumentType.PRODUCTION_RECEIPT.getValue()), documentNo);
        Long id = draft.document().getId();
        documents.putLine(id, null, new StockDocumentModels.Line(draft.document().getRevision(), command.stockId(),
                execution.getProductId(), stock.getWarehouseId(), material.getUnitId(), stock.getBatchNo(),
                stock.getBinCode(), command.quantity(), null));
        var posted = documents.approveMesReceipt(id, new StockDocumentModels.Post(command.requestId(),
                documents.detail(id).document().getRevision()));
        completion.afterInboundApproved(executionId, command.requestId());
        return posted;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public StockDocumentModels.Detail approveDocument(Long documentId, StockDocumentModels.Post command) {
        permit("business:wm:stock:document:post");
        var document = documents.detail(documentId).document();
        if (!StockDocumentType.PRODUCTION_RECEIPT.getValue().equals(document.getDocumentType())
                || document.getSourceExecutionId() == null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        Long executionId = document.getSourceExecutionId();
        completion.lockInboundChain(executionId);
        var result = documents.approveMesReceipt(documentId, command);
        if (!StockDocumentStatus.POSTED.getValue().equals(document.getStatus()))
            completion.afterInboundApproved(executionId, command.requestId());
        return result;
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
