package dev.mesbase.business.directory.service;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.system.domain.SysUserCacheResource;

import dev.mesbase.business.directory.domain.DirectoryStatus;
import dev.mesbase.business.directory.domain.DirectoryDeletionFlag;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.directory.domain.DirectoryPage;
import dev.mesbase.business.directory.domain.DirectoryQuery;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.system.mapper.SysDeptMapper;
import dev.mesbase.system.mapper.SysPostMapper;
import dev.mesbase.system.mapper.SysUserMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.mesbase.common.cache.DetailEntityCache;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.RoutingResource;
import dev.mesbase.business.production.domain.OperationResource;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * Shared center directory reached through narrowly authorized business capabilities.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class BusinessDirectoryService {
    private static final Set<String> USER_CACHE_FIELDS = Set.of("userId", "userName", "nickName", "deptId", "status", "delFlag");
    private final SysUserMapper users;
    private final SysDeptMapper departments;
    private final SysPostMapper posts;
    private final DetailEntityCache cache;
    private final BusinessCacheProcessor businessCache;
    private final PlatformPersistence center;
    private final ObjectProvider<OwnedTransactionManager> businessTransactions;
    private final ObjectProvider<RoutingMapper> routings;
    private final ObjectProvider<OperationMapper> operations;

    public BusinessDirectoryService(SysUserMapper users, SysDeptMapper departments,
                                    SysPostMapper posts, DetailEntityCache cache,
                                    BusinessCacheProcessor businessCache, PlatformPersistence center,
                                    @Qualifier("businessTransactionManager") ObjectProvider<OwnedTransactionManager> businessTransactions,
                                    ObjectProvider<RoutingMapper> routings,
                                    ObjectProvider<OperationMapper> operations) {
        this.users = users;
        this.departments = departments;
        this.posts = posts;
        this.cache = cache;
        this.businessCache = businessCache;
        this.center = center;
        this.businessTransactions = businessTransactions;
        this.routings = routings;
        this.operations = operations;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysUser> personnel(DirectoryQuery query) {
        var filter = new SysUser();
        filter.setNickName(query.keyword());
        filter.setDeptId(query.departmentId());
        filter.setStatus(DirectoryStatus.NORMAL.getValue());
        return page(query, "u.user_id desc", () -> users.selectUserList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysDept> departments(DirectoryQuery query) {
        var filter = new SysDept();
        filter.setDeptName(query.keyword());
        filter.setStatus(DirectoryStatus.NORMAL.getValue());
        return page(query, "d.dept_id desc", () -> departments.selectDeptList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysPost> posts(DirectoryQuery query) {
        var filter = new SysPost();
        filter.setPostName(query.keyword());
        filter.setStatus(DirectoryStatus.NORMAL.getValue());
        return page(query, "post_id desc", () -> posts.selectPostList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryLabel personnel(Long id) {
        var row = users.selectUserById(id);
        if (row == null || !DirectoryDeletionFlag.NORMAL.getValue()
                .equals(row.getDelFlag()) || !DirectoryStatus.NORMAL.getValue().equals(row.getStatus())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        center.transactionManager().verifyAccess();
        var safe = safeUser(row);
        SysUser cached = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? cache.getAll(SysUserCacheResource.PREFIX, List.of(id), SysUser.class, SysUser::getUserId, missing -> Map.of(id, safe), USER_CACHE_FIELDS)
                .get(id)
                : safe;
        DirectoryLabel label = userLabel(cached);
        center.transactionManager().verifyAccess();
        return label;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SysDept department(Long id) {
        return requireDepartment(id);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SysPost post(Long id) {
        return requirePost(id);
    }

    private SysDept requireDepartment(Long id) {
        var row = departments.selectDeptById(id);
        if (row == null || !DirectoryDeletionFlag.NORMAL.getValue()
                .equals(row.getDelFlag()) || !DirectoryStatus.NORMAL.getValue().equals(row.getStatus())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    private SysPost requirePost(Long id) {
        var row = posts.selectPostById(id);
        if (row == null || !DirectoryStatus.NORMAL.getValue().equals(row.getStatus())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<DirectoryLabel> historicalUsers(String keyword, int offset, int limit) {
        if (keyword != null && keyword.length() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var filter = new SysUser();
        filter.setNickName(keyword);
        var rows = page(new DirectoryQuery(null, null, offset, limit), "u.user_id desc", () -> users.selectUserList(filter));
        return new DirectoryPage<>(rows.items().stream()
                .map(user -> new DirectoryLabel(user.getUserId(), user.getUserName(), user.getNickName(), null,
                        null, user.getStatus(), user.getDeptId(), user.getDelFlag()))
                .toList(), rows.total(), rows.offset(), rows.limit());
    }

    /**
     * Uncached existence query for historical facts; current roles and capability are separate rules.
     */
    public boolean historicalUserExists(Long id) {
        if (id == null) return false;
        if (center.transactionManager().foreignTransactionActive())
            return center.readOnly(session -> session.mapper(SysUserMapper.class).selectUserById(id) != null);
        return users.selectUserById(id) != null;
    }

    /**
     * Uncached bounded historical identity check, including disabled/deleted accounts.
     */
    public boolean historicalUsersExist(Collection<Long> ids) {
        validateIds(ids);
        if (ids.stream().anyMatch(Objects::isNull)) return false;
        var distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) return true;
        Function<SysUserMapper, Boolean> query = mapper -> mapper.selectUserReferencesByIds(distinct).stream()
                .map(SysUser::getUserId).collect(Collectors.toSet()).containsAll(distinct);
        if (center.transactionManager().foreignTransactionActive())
            return center.readOnly(session -> query.apply(session.mapper(SysUserMapper.class)));
        return query.apply(users);
    }

    public DirectoryLabel userLabel(Long id) {
        return userLabels(List.of(id)).get(id);
    }

    public Map<Long, DirectoryLabel> userLabels(Collection<Long> ids) {
        validateIds(ids);
        if (center.transactionManager().foreignTransactionActive())
            return center.readOnly(session -> guardedUserLabels(ids, session.mapper(SysUserMapper.class)));
        return guardedUserLabels(ids, users);
    }

    private Map<Long, DirectoryLabel> guardedUserLabels(Collection<Long> ids, SysUserMapper mapper) {
        center.transactionManager().verifyAccess();
        Function<List<Long>, Map<Long, SysUser>> loader = missing -> {
            var result = new LinkedHashMap<Long, SysUser>();
            for (var user : mapper.selectUserReferencesByIds(missing)) result.put(user.getUserId(), safeUser(user));
            return result;
        };
        Map<Long, SysUser> rows;
        if (TransactionSynchronizationManager.isActualTransactionActive() && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            var distinct = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
            rows = distinct.isEmpty() ? Map.of() : loader.apply(distinct);
        } else
            rows = cache.getAll(SysUserCacheResource.PREFIX, ids, SysUser.class, SysUser::getUserId, loader, USER_CACHE_FIELDS);
        center.transactionManager().verifyAccess();
        return labels(rows, BusinessDirectoryService::userLabel);
    }

    private static SysUser safeUser(SysUser row) {
        var safe = new SysUser();
        safe.setUserId(row.getUserId());
        safe.setUserName(row.getUserName());
        safe.setNickName(row.getNickName());
        safe.setDeptId(row.getDeptId());
        safe.setStatus(row.getStatus());
        safe.setDelFlag(row.getDelFlag());
        return safe;
    }

    private static DirectoryLabel userLabel(SysUser user) {
        return new DirectoryLabel(user.getUserId(), user.getUserName(), user.getNickName(), null, null,
                user.getStatus(), user.getDeptId(), user.getDelFlag());
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DirectoryLabel routingLabel(Long id) {
        return routingLabels(List.of(id)).get(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Map<Long, DirectoryLabel> routingLabels(Collection<Long> ids) {
        var rows = scoped(RoutingResource.PREFIX, ids, RoutingEntity.class, RoutingEntity::getId, missing -> {
            var filter = new RoutingExample();
            filter.createCriteria().andIdIn(missing);
            var result = new LinkedHashMap<Long, RoutingEntity>();
            for (var row : routings.getObject().selectByExample(filter)) result.put(row.getId(), row);
            return result;
        });
        return labels(rows, row -> new DirectoryLabel(row.getId(), row.getCode(), row.getName(), row.getVersion(), row.getDescription(), row.getStatus(), null, null));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DirectoryLabel operationLabel(Long id) {
        return operationLabels(List.of(id)).get(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Map<Long, DirectoryLabel> operationLabels(Collection<Long> ids) {
        var rows = scoped(OperationResource.PREFIX, ids, OperationEntity.class, OperationEntity::getId, missing -> {
            var filter = new OperationExample();
            filter.createCriteria().andIdIn(missing);
            var result = new LinkedHashMap<Long, OperationEntity>();
            for (var row : operations.getObject().selectByExample(filter)) result.put(row.getId(), row);
            return result;
        });
        return labels(rows, row -> new DirectoryLabel(row.getId(), row.getCode(), row.getName(), null, row.getDescription(), row.getStatus(), null, null));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DirectoryPage<DirectoryLabel> routings(DirectoryQuery query) {
        var filter = new RoutingExample();
        if (StringUtils.isNotBlank(query.keyword())) {
            filter.createCriteria().andNameLike("%" + query.keyword() + "%");
            filter.or().andCodeLike("%" + query.keyword() + "%");
        }
        var rows = page(query, "id desc", () -> routings.getObject().selectByExample(filter));
        return new DirectoryPage<>(rows.items().stream()
                .map(row -> new DirectoryLabel(row.getId(), row.getCode(), row.getName(), row.getVersion(), row.getDescription(), row.getStatus(), null, null))
                .toList(), rows.total(), rows.offset(), rows.limit());
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DirectoryPage<DirectoryLabel> operations(DirectoryQuery query) {
        var filter = new OperationExample();
        if (StringUtils.isNotBlank(query.keyword())) {
            filter.createCriteria().andNameLike("%" + query.keyword() + "%");
            filter.or().andCodeLike("%" + query.keyword() + "%");
        }
        var rows = page(query, "id desc", () -> operations.getObject().selectByExample(filter));
        return new DirectoryPage<>(rows.items().stream()
                .map(row -> new DirectoryLabel(row.getId(), row.getCode(), row.getName(), null, row.getDescription(), row.getStatus(), null, null))
                .toList(), rows.total(), rows.offset(), rows.limit());
    }

    private <T> Map<Long, T> scoped(String resource, Collection<Long> ids, Class<T> type, Function<T, Long> identity,
                                    Function<List<Long>, Map<Long, T>> loader) {
        validateIds(ids);
        businessTransactions.getObject().verifyAccess();
        Map<Long, T> result;
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            var distinct = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
            result = distinct.isEmpty() ? Map.of() : loader.apply(distinct);
        } else result = cache.getAll(businessCache.prefix(resource), ids, type, identity, loader);
        businessTransactions.getObject().verifyAccess();
        return result;
    }

    private static <T> Map<Long, DirectoryLabel> labels(Map<Long, T> rows, Function<T, DirectoryLabel> convert) {
        var result = new LinkedHashMap<Long, DirectoryLabel>();
        rows.forEach((id, row) -> result.put(id, convert.apply(row)));
        return Map.copyOf(result);
    }

    private static void validateIds(Collection<Long> ids) {
        if (ids == null || ids.stream().filter(Objects::nonNull).distinct().limit(201).count() > 200)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static <T> DirectoryPage<T> page(DirectoryQuery query, String order, Supplier<List<T>> select) {
        if (query.pageOffset() < 0 || query.pageLimit() < 1 || query.pageLimit() > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        try {
            var result = PageHelper.<T>offsetPage(query.pageOffset(), query.pageLimit()).setOrderBy(order)
                    .<T>doSelectPage(() -> select.get());
            return new DirectoryPage<>(List.copyOf(result), result.getTotal(), query.pageOffset(), query.pageLimit());
        } finally {
            PageHelper.clearPage();
        }
    }
}
