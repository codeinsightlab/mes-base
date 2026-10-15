package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.production.OperationUserDirectoryMapper;
import dev.mesbase.system.production.OperationUserDirectoryMapper.UserOption;
import dev.mesbase.system.service.MenuScopePolicy;
import dev.mesbase.common.core.domain.entity.SysMenu;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Independent read-only center metadata window, including inside a business transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperationUserDirectory {
    private final PlatformPersistence center;

    public OperationUserDirectory(PlatformPersistence center) {
        this.center = center;
    }

    public record UserPage(List<UserOption> items, long total, int offset, int limit) {
    }

    public UserPage page(String keyword, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100 || keyword != null && keyword.length() > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        return read(null, keyword, offset, limit, null);
    }

    public void requireValid(Long userId) {
        if (userId == null || read(userId, null, 0, 1, null).items().isEmpty())
            throw new FoundationException(ErrorCode.OPERATOR_NOT_AUTHORIZED);
    }

    public List<UserOption> validUsers(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        if (ids.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return read(null, null, 0, 100, ids).items();
    }

    private UserPage read(Long id, String keyword, int offset, int limit, List<Long> userIds) {
        String factory = FactoryContext.required().factoryId().value();
        return center.readOnly(session -> {
            var menus = session.mapper(SysMenuMapper.class).selectMenuList(new SysMenu());
            var policy = new MenuScopePolicy(menus);
            var ids = menus.stream().filter(m -> "0".equals(m.getStatus()) && m.getPerms() != null
                    && !m.getPerms().isBlank() && !policy.platform(m)).map(SysMenu::getMenuId).toList();
            if (ids.isEmpty()) return new UserPage(List.of(), 0, offset, limit);
            try (Page<UserOption> page = PageHelper.offsetPage(offset, limit)) {
                var rows = session.mapper(OperationUserDirectoryMapper.class)
                        .select(factory, id, keyword, ids, userIds);
                return new UserPage(List.copyOf(rows), page.getTotal(), offset, limit);
            }
        });
    }
}
