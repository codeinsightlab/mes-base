package dev.mesbase.server;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.RequestDispatcher;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.platform.web.*;
import org.springframework.boot.webmvc.error.ErrorController;
@RestController
public final class SafeErrorController implements ErrorController {
    @RequestMapping("/error") @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object status=request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        ErrorCode code=status instanceof Integer number && number==404 ? ErrorCode.NOT_FOUND : ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.httpStatus()).body(ErrorWriter.body(code,request));
    }
}
