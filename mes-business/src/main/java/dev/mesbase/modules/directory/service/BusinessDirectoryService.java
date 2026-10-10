package dev.mesbase.modules.directory.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.directory.domain.DirectoryPage;
import dev.mesbase.modules.directory.domain.DirectoryQuery;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.system.mapper.SysDeptMapper;
import dev.mesbase.system.mapper.SysPostMapper;
import dev.mesbase.system.mapper.SysUserMapper;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared center directory reached through narrowly authorized business capabilities.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class BusinessDirectoryService {
    private final SysUserMapper users;
    private final SysDeptMapper departments;
    private final SysPostMapper posts;

    public BusinessDirectoryService(SysUserMapper users, SysDeptMapper departments,
                                    SysPostMapper posts) {
        this.users = users;
        this.departments = departments;
        this.posts = posts;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysUser> personnel(DirectoryQuery query) {
        var filter = new SysUser();
        filter.setNickName(query.keyword());
        filter.setDeptId(query.departmentId());
        filter.setStatus("0");
        return page(query, "u.user_id asc", () -> users.selectUserList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysDept> departments(DirectoryQuery query) {
        var filter = new SysDept();
        filter.setDeptName(query.keyword());
        filter.setStatus("0");
        return page(query, "d.dept_id asc", () -> departments.selectDeptList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public DirectoryPage<SysPost> posts(DirectoryQuery query) {
        var filter = new SysPost();
        filter.setPostName(query.keyword());
        filter.setStatus("0");
        return page(query, "post_id asc", () -> posts.selectPostList(filter));
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SysUser personnel(Long id) {
        var row = users.selectUserById(id);
        if (row == null || !"0".equals(row.getDelFlag()) || !"0".equals(row.getStatus()))
            throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
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
        if (row == null || !"0".equals(row.getDelFlag()) || !"0".equals(row.getStatus())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    private SysPost requirePost(Long id) {
        var row = posts.selectPostById(id);
        if (row == null || !"0".equals(row.getStatus())) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private static <T> DirectoryPage<T> page(DirectoryQuery query, String order, Supplier<List<T>> select) {
        if (query.pageOffset() < 0 || query.pageLimit() < 1 || query.pageLimit() > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        try {
            var result = PageHelper.<T>offsetPage(query.pageOffset(), query.pageLimit()).setOrderBy(order)
                    .doSelectPage(() -> select.get());
            return new DirectoryPage<>(List.copyOf(result), result.getTotal(), query.pageOffset(), query.pageLimit());
        } finally {
            PageHelper.clearPage();
        }
    }
}
