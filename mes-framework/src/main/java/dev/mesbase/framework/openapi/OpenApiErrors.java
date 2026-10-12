package dev.mesbase.framework.openapi;

import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.core.error.FoundationException;
import jakarta.servlet.ServletException;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

final class OpenApiErrors {
    private OpenApiErrors() {
    }

    static OpenApiError classify(Throwable failure) {
        if (failure instanceof OpenApiException) return ((OpenApiException) failure).getError();
        if (failure instanceof PersistenceException
                || failure instanceof SQLException || failure instanceof DataAccessException)
            return OpenApiError.DEPENDENCY_UNAVAILABLE;
        if (failure instanceof FoundationException) {
            var code = ((FoundationException) failure).code();
            return switch (code) {
                case UNAUTHENTICATED -> OpenApiError.INVALID_CREDENTIALS;
                case FORBIDDEN -> OpenApiError.ACCESS_DENIED;
                case BAD_REQUEST -> OpenApiError.INVALID_REQUEST;
                default -> OpenApiError.INTERNAL_ERROR;
            };
        }
        if (failure instanceof AccessDeniedException) return OpenApiError.ACCESS_DENIED;
        if (failure instanceof ServiceException) {
            Integer code = ((ServiceException) failure).getCode();
            return Integer.valueOf(403).equals(code) ? OpenApiError.ACCESS_DENIED : OpenApiError.INVALID_REQUEST;
        }
        if (failure instanceof BindException || failure instanceof MethodArgumentNotValidException
                || failure instanceof ConstraintViolationException || failure instanceof HttpMessageNotReadableException
                || failure instanceof HttpRequestMethodNotSupportedException || failure instanceof HttpMediaTypeNotSupportedException
                || failure instanceof ServletRequestBindingException || failure instanceof MethodArgumentTypeMismatchException)
            return OpenApiError.INVALID_REQUEST;
        if (failure instanceof ServletException && failure.getCause() != null)
            return classify(failure.getCause());
        return OpenApiError.INTERNAL_ERROR;
    }
}
