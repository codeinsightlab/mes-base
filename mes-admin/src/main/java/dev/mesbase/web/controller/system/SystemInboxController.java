package dev.mesbase.web.controller.system;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;


import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.framework.websocket.MessageEventHub;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.service.impl.SysMessageServiceImpl;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.message.SysMessageAssembler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequestMapping({"/api/system/inbox","/api/mobile/system/message"}) @ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemInboxController extends BaseController {
 private final MessageEventHub events;
 private final SysMessageServiceImpl messages;public SystemInboxController(SysMessageServiceImpl m,MessageEventHub events){messages=m;this.events=events;}
 @GetMapping({"","/getMyMessage"}) @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public TableDataInfo list(SysMessage query){startPage();return PageResultMapper.map(messages.myMessages(query), SysMessageAssembler::toResult);}
 @PutMapping("/{id}/read") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public AjaxResult read(@PathVariable Long id){return toAjax(messages.markRead(id));}
 @GetMapping(value="/events",produces="text/event-stream") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public SseEmitter events(HttpServletRequest request){String header=request.getHeader("Authorization");return events.connect(header.substring(7));}
}
