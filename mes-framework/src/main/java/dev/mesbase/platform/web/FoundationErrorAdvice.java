package dev.mesbase.platform.web;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.common.exception.ServiceException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public final class FoundationErrorAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(FoundationErrorAdvice.class);

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncResponseClosed(AsyncRequestNotUsableException failure, HttpServletRequest request) {
        LOG.info("Async response no longer writable requestId={}", ErrorWriter.requestId(request));
        LOG.debug("Async response exception chain requestId={}", ErrorWriter.requestId(request), failure);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponse> sourceBusiness(ServiceException failure, HttpServletRequest request) {
        int status = failure.getCode() != null && failure.getCode() == 401 ? 401 : failure.getCode() != null && failure.getCode() == 403 ? 403 : 422;
        String code = status == 422 ? "BUSINESS_REJECTED" : status == 401 ? "UNAUTHENTICATED" : "FORBIDDEN";
        LOG.info("event=http.request.rejected requestId={} errorCode={}", ErrorWriter.requestId(request), code);
        String message = failure.getMessage() == null ? "当前操作不可执行" : failure.getMessage();
        if (message.length() > 500) message = message.substring(0, 500);
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, ErrorWriter.requestId(request)));
    }

    @ExceptionHandler(FoundationException.class)
    public ResponseEntity<ErrorResponse> known(FoundationException failure, HttpServletRequest request) {
        HttpFailureLogging.known(request, failure);
        return error(failure.code(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception failure, HttpServletRequest request) {
        LOG.info("event=http.request.rejected errorCode=BAD_REQUEST requestId={} type={}", ErrorWriter.requestId(request), failure.getClass()
                .getSimpleName());
        return error(ErrorCode.BAD_REQUEST, request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> missing(Exception failure, HttpServletRequest request) {
        return error(ErrorCode.NOT_FOUND, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(Exception failure, HttpServletRequest request) {
        return ResponseEntity.status(405)
                .body(new ErrorResponse("METHOD_NOT_ALLOWED", "Method not allowed", ErrorWriter.requestId(request)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> media(Exception failure, HttpServletRequest request) {
        return ResponseEntity.status(415)
                .body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", ErrorWriter.requestId(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> uploadSize(Exception failure, HttpServletRequest request) {
        return ResponseEntity.status(413)
                .body(new ErrorResponse("UPLOAD_TOO_LARGE", "Upload exceeds configured limit", ErrorWriter.requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception failure, HttpServletRequest request) {
        HttpFailureLogging.unexpected(request, failure);
        return error(ErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> error(ErrorCode code, HttpServletRequest request) {
        code = ErrorWriter.responseCode(code, request);
        var response = ResponseEntity.status(code.httpStatus());
        if (code == ErrorCode.UNAUTHENTICATED)
            response.header("WWW-Authenticate", "Bearer".equals(request.getAttribute("mes.authScheme")) ? "Bearer realm=mes-base" : "Basic realm=mes-base");
        return response.body(ErrorWriter.body(code, request));
    }
}
