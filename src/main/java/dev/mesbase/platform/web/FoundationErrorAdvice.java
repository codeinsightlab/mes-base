package dev.mesbase.platform.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import dev.mesbase.core.error.*;
@RestControllerAdvice
public final class FoundationErrorAdvice {
    private static final Logger LOG=LoggerFactory.getLogger(FoundationErrorAdvice.class);
    @ExceptionHandler(FoundationException.class)
    public ResponseEntity<ErrorResponse> known(FoundationException failure,HttpServletRequest request) { LOG.warn("Foundation failure requestId={}",ErrorWriter.requestId(request),failure); return error(failure.code(),request); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentNotValidException.class,MethodArgumentTypeMismatchException.class,HandlerMethodValidationException.class,ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception failure,HttpServletRequest request) { LOG.warn("Invalid request requestId={} type={}",ErrorWriter.requestId(request),failure.getClass().getSimpleName()); return error(ErrorCode.BAD_REQUEST,request); }
    @ExceptionHandler({NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> missing(Exception failure,HttpServletRequest request) { return error(ErrorCode.NOT_FOUND,request); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(Exception failure,HttpServletRequest request) { return ResponseEntity.status(405).body(new ErrorResponse("METHOD_NOT_ALLOWED","Method not allowed",ErrorWriter.requestId(request))); }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> media(Exception failure,HttpServletRequest request) { return ResponseEntity.status(415).body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE","Unsupported media type",ErrorWriter.requestId(request))); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> uploadSize(Exception failure,HttpServletRequest request) { return ResponseEntity.status(413).body(new ErrorResponse("UPLOAD_TOO_LARGE","Upload exceeds configured limit",ErrorWriter.requestId(request))); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception failure,HttpServletRequest request) { LOG.error("Unhandled failure requestId={}",ErrorWriter.requestId(request),failure); return error(ErrorCode.INTERNAL_ERROR,request); }
    private ResponseEntity<ErrorResponse> error(ErrorCode code,HttpServletRequest request) {
        var response=ResponseEntity.status(code.httpStatus());
        if(code==ErrorCode.UNAUTHENTICATED) response.header("WWW-Authenticate","Basic realm=mes-base");
        return response.body(ErrorWriter.body(code,request));
    }
}
