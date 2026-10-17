package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.ProductionQuantityConflictException;
import dev.mesbase.business.production.domain.ProductionQualityPendingException;
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
