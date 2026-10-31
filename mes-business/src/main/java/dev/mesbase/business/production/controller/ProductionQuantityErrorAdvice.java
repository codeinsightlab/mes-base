package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.ProductionClosureBlockedException;
import dev.mesbase.business.production.domain.ProductionCombinationStockChangedException;
import dev.mesbase.business.production.domain.ProductionQualityPendingException;
import dev.mesbase.business.production.domain.ProductionQuantityConflictException;
import dev.mesbase.platform.web.ErrorResponse;
import dev.mesbase.platform.web.ErrorWriter;
import dev.mesbase.platform.web.HttpFailureLogging;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionQuantityErrorAdvice {
    public record StockChanged(String errorCode, String message, String requestId, String maximumQty) {
    }

    @ExceptionHandler(ProductionCombinationStockChangedException.class)
    public ResponseEntity<StockChanged> stockChanged(ProductionCombinationStockChangedException failure,
                                                     HttpServletRequest request) {
        HttpFailureLogging.known(request, failure);
        return ResponseEntity.status(failure.code().httpStatus()).body(new StockChanged(failure.code().name(),
                failure.safeMessage(), ErrorWriter.requestId(request), failure.maximumQty().toPlainString()));
    }

    @ExceptionHandler(ProductionClosureBlockedException.class)
    public ResponseEntity<ErrorResponse> closure(ProductionClosureBlockedException failure,
                                                 HttpServletRequest request) {
        HttpFailureLogging.known(request, failure);
        return ResponseEntity.status(failure.code().httpStatus()).body(new ErrorResponse(failure.code()
                .name(), failure.safeExplanation(), ErrorWriter.requestId(request)));
    }

    @ExceptionHandler(ProductionQuantityConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ProductionQuantityConflictException failure,
                                                  HttpServletRequest request) {
        HttpFailureLogging.known(request, failure);
        return ResponseEntity.status(failure.code().httpStatus()).body(new ErrorResponse(failure.code()
                .name(), failure.safeExplanation(), ErrorWriter.requestId(request)));
    }

    @ExceptionHandler(ProductionQualityPendingException.class)
    public ResponseEntity<ErrorResponse> qualityPending(ProductionQualityPendingException failure,
                                                        HttpServletRequest request) {
        HttpFailureLogging.known(request, failure);
        return ResponseEntity.status(failure.code().httpStatus()).body(new ErrorResponse(failure.code().name(),
                failure.safeExplanation(), ErrorWriter.requestId(request)));
    }
}
