package dev.mesbase.business.directory.controller;

import dev.mesbase.business.directory.domain.DirectoryPage;
import dev.mesbase.business.directory.domain.DirectoryQuery;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.SysPost;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.function.Function;

@RestController
@RequestMapping("/api/business/directory")
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class BusinessDirectoryController {
    public static final String PERSONNEL_READ = "business:directory:personnel:read";
    public static final String DEPARTMENT_READ = "business:directory:department:read";
    public static final String POST_READ = "business:directory:post:read";
    private final BusinessDirectoryService service;

    public BusinessDirectoryController(BusinessDirectoryService service) {
        this.service = service;
    }

    @GetMapping("/personnel")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERSONNEL_READ)
    public DirectoryPage<DirectoryOption> personnel(@Valid @ModelAttribute DirectoryQuery query) {
        return view(service.personnel(query), BusinessDirectoryController::personnelView);
    }

    @GetMapping("/personnel/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERSONNEL_READ)
    public DirectoryOption personnel(@PathVariable Long id) {
        return personnelView(service.personnel(id));
    }

    @GetMapping("/departments")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = DEPARTMENT_READ)
    public DirectoryPage<DirectoryOption> departments(@Valid @ModelAttribute DirectoryQuery query) {
        return view(service.departments(query), BusinessDirectoryController::departmentView);
    }

    @GetMapping("/departments/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = DEPARTMENT_READ)
    public DirectoryOption department(@PathVariable Long id) {
        return departmentView(service.department(id));
    }

    @GetMapping("/posts")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = POST_READ)
    public DirectoryPage<DirectoryOption> posts(@Valid @ModelAttribute DirectoryQuery query) {
        return view(service.posts(query), BusinessDirectoryController::postView);
    }

    @GetMapping("/posts/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = POST_READ)
    public DirectoryOption post(@PathVariable Long id) {
        return postView(service.post(id));
    }

    private static DirectoryOption personnelView(SysUser row) {
        return new DirectoryOption(id(row.getUserId()), row.getNickName(), row.getUserName(),
                null, id(row.getDeptId()));
    }

    private static DirectoryOption departmentView(SysDept row) {
        return new DirectoryOption(id(row.getDeptId()), row.getDeptName(), null,
                id(row.getParentId()), null);
    }

    private static DirectoryOption postView(SysPost row) {
        return new DirectoryOption(id(row.getPostId()), row.getPostName(), row.getPostCode(), null, null);
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static <T> DirectoryPage<DirectoryOption> view(DirectoryPage<T> page,
                                                           Function<T, DirectoryOption> convert) {
        return new DirectoryPage<>(page.items().stream().map(convert).toList(),
                page.total(), page.offset(), page.limit());
    }
}
