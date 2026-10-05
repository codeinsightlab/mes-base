package dev.mesbase.platform.management;
import org.springframework.web.bind.annotation.*;import jakarta.validation.Valid;
import dev.mesbase.platform.management.PlatformContracts.*;import dev.mesbase.platform.web.*;
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.platform.enabled",havingValue="true")
@RestController @RequestMapping("/api/platform/session")
public final class SessionController {
 private final SessionService sessions;public SessionController(SessionService sessions){this.sessions=sessions;}
 @PostMapping @EndpointAccess(EndpointAccess.Kind.PUBLIC) public SessionView login(@Valid @RequestBody Login input,jakarta.servlet.http.HttpServletRequest request){return sessions.login(input,(String)request.getAttribute("mes.requestId"));}
 @DeleteMapping @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED) @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void logout(@RequestHeader("Authorization")String authorization){if(!authorization.startsWith("Bearer "))throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.BAD_REQUEST);sessions.logout(authorization.substring(7));}
}
