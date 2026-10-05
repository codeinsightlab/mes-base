package dev.mesbase.web.controller.system;
import dev.mesbase.common.core.controller.BaseController;import dev.mesbase.common.core.domain.AjaxResult;import dev.mesbase.common.core.page.TableDataInfo;import dev.mesbase.platform.web.EndpointAccess;import dev.mesbase.system.domain.SysMessage;import dev.mesbase.system.service.impl.SysMessageServiceImpl;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping({"/api/system/inbox","/api/mobile/system/message"}) @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemInboxController extends BaseController {
 private final dev.mesbase.framework.websocket.MessageEventHub events;
 private final SysMessageServiceImpl messages;public SystemInboxController(SysMessageServiceImpl m,dev.mesbase.framework.websocket.MessageEventHub events){messages=m;this.events=events;}
 @GetMapping({"","/getMyMessage"}) @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public TableDataInfo list(SysMessage query){startPage();return getDataTable(messages.myMessages(query));}
 @PutMapping("/{id}/read") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public AjaxResult read(@PathVariable Long id){return toAjax(messages.markRead(id));}
 @GetMapping(value="/events",produces="text/event-stream") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(jakarta.servlet.http.HttpServletRequest request){String header=request.getHeader("Authorization");return events.connect(header.substring(7));}
}
