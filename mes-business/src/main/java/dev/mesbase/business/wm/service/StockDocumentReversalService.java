package dev.mesbase.business.wm.service;

import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Inventory-only posted-document reversal. Callers own any business lifecycle checks.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class)
public class StockDocumentReversalService {
    private final WmStockDocumentMapper documents;
    private final LocalStockService stock;
    private final Validator validator;
    private final ObjectMapper json = new ObjectMapper();

    public StockDocumentReversalService(WmStockDocumentMapper documents, LocalStockService stock,
                                        Validator validator) {
        this.documents = documents;
        this.stock = stock;
        this.validator = validator;
    }

    public WmStockDocumentEntity reverse(Long documentId, StockDocumentModels.Cancellation command) {
        validate(documentId, command);
        String reason = command.reason().trim();
        String requestId = uuid(command.requestId());
        Long actor = SecurityUtils.getUserId();
        String requestHash = RequestFingerprint.sha256(json.writeValueAsString(
                List.of(documentId, requestId, actor, reason)));

        var document = lock(documentId);
        if (StockDocumentStatus.REVERSED.getValue().equals(document.getStatus()))
            return replay(document, requestId, requestHash);
        if (!StockDocumentStatus.POSTED.getValue().equals(document.getStatus())
                || !Objects.equals(document.getRevision(), command.expectedRevision()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);

        stock.reverseDocument(document, requestId, reason);
        LocalDateTime now = LocalDateTime.now();
        document.setStatus(StockDocumentStatus.REVERSED.getValue());
        document.setReversedBy(actor);
        document.setReversedAt(now);
        document.setReversalReason(reason);
        document.setReversalRequestId(requestId);
        document.setReversalRequestHash(requestHash);
        document.setRevision(Math.addExact(document.getRevision(), 1));
        document.setUpdateBy(actor.toString());
        document.setUpdateTime(now);
        try {
            if (documents.updateByPrimaryKeyWithBLOBs(document) != 1)
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, conflict);
        }
        return document;
    }

    private WmStockDocumentEntity replay(WmStockDocumentEntity document, String requestId, String requestHash) {
        if (!Objects.equals(requestId, document.getReversalRequestId())
                || !Objects.equals(requestHash, document.getReversalRequestHash()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        return document;
    }

    private WmStockDocumentEntity lock(Long documentId) {
        if (documentId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var document = documents.selectByPrimaryKeyForUpdate(documentId);
        if (document == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return document;
    }

    private void validate(Long documentId, StockDocumentModels.Cancellation command) {
        if (documentId == null || command == null || !validator.validate(command).isEmpty())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                    new SafeBusinessMessage("库存撤销请求无效，请检查请求标识、版本和原因"));
        if (command.reason() == null || command.reason().trim().isEmpty())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                    new SafeBusinessMessage("撤销已过账库存单据必须填写撤销原因"));
        if (!uuid(command.requestId()).equals(command.requestId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                    new SafeBusinessMessage("撤销请求标识格式无效，请使用标准UUID"));
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure,
                    new SafeBusinessMessage("撤销请求标识格式无效，请使用标准UUID"));
        }
    }
}
