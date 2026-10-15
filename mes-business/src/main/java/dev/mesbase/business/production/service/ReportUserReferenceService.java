package dev.mesbase.business.production.service;

import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Objects;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historical personnel existence, deliberately independent of present capability and assignment.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ReportUserReferenceService {
    private final SysUserMapper users;
    private final PlatformPersistence center;

    public ReportUserReferenceService(SysUserMapper users, PlatformPersistence center) {
        this.users = users;
        this.center = center;
    }

    public record HistoricalUser(Long id, String name) {
    }

    public record UserPage(List<HistoricalUser> items, long total, int offset, int limit) {
    }

    public void requireExists(Long id) {
        if (id == null) throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
        var user = center.transactionManager().foreignTransactionActive()
                ? center.readOnly(s -> s.mapper(SysUserMapper.class).selectUserById(id)) : users.selectUserById(id);
        if (user == null) throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
    }

    /**
     * A page has at most 100 ledger rows and two referenced users per row. No long-lived identity cache.
     */
    public Map<Long, String> names(Collection<Long> userIds) {
        var ids = userIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) return Map.of();
        if (ids.size() > 200) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var rows = center.transactionManager().foreignTransactionActive()
                ? center.readOnly(session -> session.mapper(SysUserMapper.class).selectUserNamesByIds(ids))
                : users.selectUserNamesByIds(ids);
        var result = new LinkedHashMap<Long, String>();
        for (var user : rows) {
            if (user.getNickName() != null && !user.getNickName().isBlank())
                result.put(user.getUserId(), user.getNickName());
        }
        return Map.copyOf(result);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public UserPage page(String keyword, int offset, int limit) {
        ProductionQuantityService.paging(offset, limit);
        if (keyword != null && keyword.length() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var filter = new SysUser();
        filter.setNickName(keyword);
        try (var page = PageHelper.offsetPage(offset, limit).setOrderBy("u.user_id asc")) {
            var rows = users.selectUserList(filter);
            return new UserPage(rows.stream().map(u -> new HistoricalUser(u.getUserId(), u.getNickName()))
                    .toList(), page.getTotal(), offset, limit);
        }
    }
}
