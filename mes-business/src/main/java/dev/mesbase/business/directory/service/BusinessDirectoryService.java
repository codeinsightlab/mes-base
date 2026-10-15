package dev.mesbase.business.directory.service;

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
import java.util.LinkedHashMap;
import java.util.function.Function;

import dev.mesbase.common.cache.ReferenceLabelCache;
import dev.mesbase.common.cache.ReferenceLabelCache.Label;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.RoutingResource;
import dev.mesbase.business.production.domain.OperationResource;
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
    private final SysUserMapper users;
    private final SysDeptMapper departments;
    private final SysPostMapper posts;
    private final ReferenceLabelCache cache;
    private final BusinessCacheProcessor businessCache;
    private final PlatformPersistence center;
    private final ObjectProvider<OwnedTransactionManager> businessTransactions;
    private final ObjectProvider<RoutingMapper> routings;
    private final ObjectProvider<OperationMapper> operations;

    public BusinessDirectoryService(SysUserMapper users, SysDeptMapper departments,
                                    SysPostMapper posts, ReferenceLabelCache cache,
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
        return page(query, "u.user_id asc", () -> users.selectUserList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysDept> departments(DirectoryQuery query) {
        var filter = new SysDept();
        filter.setDeptName(query.keyword());
        filter.setStatus(DirectoryStatus.NORMAL.getValue());
        return page(query, "d.dept_id asc", () -> departments.selectDeptList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysPost> posts(DirectoryQuery query) {
        var filter = new SysPost();
        filter.setPostName(query.keyword());
        filter.setStatus(DirectoryStatus.NORMAL.getValue());
        return page(query, "post_id asc", () -> posts.selectPostList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Label personnel(Long id) {
        var row = users.selectUserById(id);
        if (row == null || !DirectoryDeletionFlag.NORMAL.getValue()
                .equals(row.getDelFlag()) || !DirectoryStatus.NORMAL.getValue().equals(row.getStatus())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        center.transactionManager().verifyAccess();
        Label label = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? cache.getAll(ReferenceLabelCache.USERS, List.of(id), missing -> Map.of(id, userLabel(row))).get(id)
                : userLabel(row);
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
    public DirectoryPage<Label> historicalUsers(String keyword, int offset, int limit) {
        if (keyword != null && keyword.length() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var filter = new SysUser();
        filter.setNickName(keyword);
        var rows = page(new DirectoryQuery(null, null, offset, limit), "u.user_id asc", () -> users.selectUserList(filter));
        return new DirectoryPage<>(rows.items().stream()
                .map(user -> new Label(user.getUserId(), user.getUserName(), user.getNickName(), null,
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

    public Label userLabel(Long id) {
        return userLabels(List.of(id)).get(id);
    }

    public Map<Long, Label> userLabels(Collection<Long> ids) {
        validateIds(ids);
        if (center.transactionManager().foreignTransactionActive())
            return center.readOnly(session -> guardedUserLabels(ids, session.mapper(SysUserMapper.class)));
        return guardedUserLabels(ids, users);
    }

    private Map<Long, Label> guardedUserLabels(Collection<Long> ids, SysUserMapper mapper) {
        center.transactionManager().verifyAccess();
        Function<List<Long>, Map<Long, Label>> loader = missing -> {
            var result = new LinkedHashMap<Long, Label>();
            for (var user : mapper.selectUserReferencesByIds(missing))
                result.put(user.getUserId(), userLabel(user));
            return result;
        };
        Map<Long, Label> result;
        if (TransactionSynchronizationManager.isActualTransactionActive() && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            var distinct = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
            result = distinct.isEmpty() ? Map.of() : loader.apply(distinct);
        } else result = cache.getAll(ReferenceLabelCache.USERS, ids, loader);
        center.transactionManager().verifyAccess();
        return result;
    }

    private static Label userLabel(SysUser user) {
        return new Label(user.getUserId(), user.getUserName(), user.getNickName(), null, null,
                user.getStatus(), user.getDeptId(), user.getDelFlag());
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Label routingLabel(Long id) {
        return routingLabels(List.of(id)).get(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Map<Long, Label> routingLabels(Collection<Long> ids) {
        return scoped(RoutingResource.PREFIX, ids, missing -> {
            var filter = new RoutingExample();
            filter.createCriteria().andIdIn(missing);
            var result = new LinkedHashMap<Long, Label>();
            for (var row : routings.getObject().selectByExample(filter))
                result.put(row.getId(), new Label(row.getId(), row.getCode(), row.getName(), row.getVersion(), row.getDescription(), row.getStatus(), null, null));
            return result;
        });
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Label operationLabel(Long id) {
        return operationLabels(List.of(id)).get(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Map<Long, Label> operationLabels(Collection<Long> ids) {
        return scoped(OperationResource.PREFIX, ids, missing -> {
            var filter = new OperationExample();
            filter.createCriteria().andIdIn(missing);
            var result = new LinkedHashMap<Long, Label>();
            for (var row : operations.getObject().selectByExample(filter))
                result.put(row.getId(), new Label(row.getId(), row.getCode(), row.getName(), null, row.getDescription(), row.getStatus(), null, null));
            return result;
        });
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DirectoryPage<Label> routings(DirectoryQuery query) {
        var filter = new RoutingExample();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            filter.createCriteria().andNameLike("%" + query.keyword() + "%");
            filter.or().andCodeLike("%" + query.keyword() + "%");
        }
        var rows = page(query, "id desc", () -> routings.getObject().selectByExample(filter));
        return new DirectoryPage<>(rows.items().stream()
                .map(row -> new Label(row.getId(), row.getCode(), row.getName(), row.getVersion(), row.getDescription(), row.getStatus(), null, null))
                .toList(), rows.total(), rows.offset(), rows.limit());
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DirectoryPage<Label> operations(DirectoryQuery query) {
        var filter = new OperationExample();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            filter.createCriteria().andNameLike("%" + query.keyword() + "%");
            filter.or().andCodeLike("%" + query.keyword() + "%");
        }
        var rows = page(query, "id desc", () -> operations.getObject().selectByExample(filter));
        return new DirectoryPage<>(rows.items().stream()
                .map(row -> new Label(row.getId(), row.getCode(), row.getName(), null, row.getDescription(), row.getStatus(), null, null))
                .toList(), rows.total(), rows.offset(), rows.limit());
    }

    private Map<Long, Label> scoped(String resource, Collection<Long> ids,
                                    Function<List<Long>, Map<Long, Label>> loader) {
        validateIds(ids);
        businessTransactions.getObject().verifyAccess();
        Map<Long, Label> result;
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            var distinct = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
            result = distinct.isEmpty() ? Map.of() : loader.apply(distinct);
        } else result = cache.getAll(businessCache.directoryNamespace(resource), ids, loader);
        businessTransactions.getObject().verifyAccess();
        return result;
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
