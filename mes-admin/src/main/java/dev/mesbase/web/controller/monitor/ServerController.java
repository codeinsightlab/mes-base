package dev.mesbase.web.controller.monitor;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.framework.web.domain.Server;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器监控
 *
 * @author ktg
 */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
@RequestMapping("/api/monitor/server")
public class ServerController {
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:server:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping()
    public AjaxResult getInfo() throws Exception {
        Server server = new Server();
        server.copyTo();
        return AjaxResult.success(server);
    }
}
