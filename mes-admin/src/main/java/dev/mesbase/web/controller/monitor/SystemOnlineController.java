package dev.mesbase.web.controller.monitor;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import java.util.ArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/api/monitor/online")
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemOnlineController {
    public record Online(String tokenId, String userName, String ipaddr, String loginLocation, String browser,
                         String os, long loginTime) {
    }

    private final RedisCache cache;
    private final TokenService tokens;

    public SystemOnlineController(RedisCache cache, TokenService tokens) {
        this.cache = cache;
        this.tokens = tokens;
    }

    @GetMapping("/list")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:online:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public TableDataInfo list(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "20") int pageSize,
                              @RequestParam(required = false) String userName,
                              @RequestParam(required = false) String ipaddr) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var rows = new ArrayList<Online>();
        for (String key : cache.keys(TokenService.PREFIX + "*")) {
            TokenService.Identity identity = tokens.identity(key.substring(TokenService.PREFIX.length()));
            if (identity != null && (userName == null || identity.username()
                    .contains(userName)) && (ipaddr == null || identity.ip().contains(ipaddr)))
                rows.add(new Online(key.substring(TokenService.PREFIX.length()), identity.username(), identity.ip(), "", identity.browser(), identity.os() == null ? "Unknown" : identity.os(), identity.loginTime()));
        }
        var result = new TableDataInfo();
        result.setCode(200);
        result.setRows(rows.stream().skip((long) (pageNum - 1) * pageSize).limit(pageSize).toList());
        result.setTotal(rows.size());
        return result;
    }

    @DeleteMapping("/{tokenId}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:online:forceLogout", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult revoke(@PathVariable String tokenId) {
        if (!tokenId.matches("[a-f0-9]{64}"))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        tokens.delLoginUser(tokenId);
        return AjaxResult.success();
    }
}
