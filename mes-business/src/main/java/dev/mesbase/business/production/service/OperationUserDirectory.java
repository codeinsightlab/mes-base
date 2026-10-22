package dev.mesbase.business.production.service;

import dev.mesbase.common.core.page.PageData;
import dev.mesbase.common.utils.StringUtils;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Ordinary directory queries use injected center mappers; business transaction validation uses an independent read window.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperationUserDirectory {
    private final PlatformPersistence center;

    private final SysMenuMapper menus;
    private final OperationUserDirectoryMapper users;

    public OperationUserDirectory(PlatformPersistence center, SysMenuMapper menus, OperationUserDirectoryMapper users) {
        this.center = center;
        this.menus = menus;
        this.users = users;
    }

    public record UserPage(List<UserOption> items, long total, int offset, int limit) implements PageData<UserOption> {
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public UserPage page(String keyword, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100 || keyword != null && keyword.length() > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        return query(FactoryContext.required().factoryId()
                .value(), null, keyword, offset, limit, null, true, menus, users);
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
        if (!center.transactionManager().foreignTransactionActive())
            return query(factory, id, keyword, offset, limit, userIds, false, menus, users);
        return center.readOnly(session -> query(factory, id, keyword, offset, limit, userIds, false,
                session.mapper(SysMenuMapper.class), session.mapper(OperationUserDirectoryMapper.class)));
    }

    private UserPage query(String factory, Long id, String keyword, int offset, int limit, List<Long> userIds,
                           boolean count,
                           SysMenuMapper menuMapper, OperationUserDirectoryMapper userMapper) {
        try {
            var menuRows = menuMapper.selectMenuList(new SysMenu());
            var policy = new MenuScopePolicy(menuRows);
            var ids = menuRows.stream().filter(m -> "0".equals(m.getStatus()) && StringUtils.isNotBlank(m.getPerms()) && !policy.platform(m)).map(SysMenu::getMenuId).toList();
            if (ids.isEmpty()) return new UserPage(List.of(), 0, offset, limit);
            try (Page<UserOption> page = PageHelper.offsetPage(offset, limit, count)) {
                var rows = userMapper.select(factory, id, keyword, ids, userIds);
                return new UserPage(List.copyOf(rows), page.getTotal(), offset, limit);
            }
        } finally {
            PageHelper.clearPage();
        }
    }
}
