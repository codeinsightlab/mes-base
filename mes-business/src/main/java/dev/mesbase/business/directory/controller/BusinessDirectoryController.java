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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import dev.mesbase.common.cache.ReferenceLabelCache.Label;
import dev.mesbase.business.production.controller.OperationPermissions;
import dev.mesbase.business.production.controller.ProductionReportPermissions;
import dev.mesbase.business.production.controller.RoutingPermissions;
import java.util.List;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;

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
        var row = service.personnel(id);
        return new DirectoryOption(id(row.id()), row.name(), row.code(), null, id(row.departmentId()));
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

    @GetMapping("/routings")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.LIST)
    public DirectoryPage<ReferenceOption> routings(@Valid @ModelAttribute DirectoryQuery query) {
        return references(service.routings(query));
    }

    @GetMapping("/routings/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.LIST)
    public ReferenceOption routing(@PathVariable Long id) {
        return reference(service.routingLabel(id));
    }

    @GetMapping("/operations")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = OperationPermissions.LIST)
    public DirectoryPage<ReferenceOption> operations(@Valid @ModelAttribute DirectoryQuery query) {
        return references(service.operations(query));
    }

    @GetMapping("/operations/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = OperationPermissions.LIST)
    public ReferenceOption operation(@PathVariable Long id) {
        return reference(service.operationLabel(id));
    }

    @GetMapping("/routing-references")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = RoutingPermissions.LIST)
    public List<ReferenceOption> routingReferences(@RequestParam List<Long> ids) {
        return service.routingLabels(ids).values().stream().map(BusinessDirectoryController::reference).toList();
    }

    @GetMapping("/operation-references")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = OperationPermissions.LIST)
    public List<ReferenceOption> operationReferences(@RequestParam List<Long> ids) {
        return service.operationLabels(ids).values().stream().map(BusinessDirectoryController::reference).toList();
    }

    @GetMapping("/user-references")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = PERSONNEL_READ)
    public List<ReferenceOption> userReferences(@RequestParam List<Long> ids) {
        return service.userLabels(ids).values().stream().map(BusinessDirectoryController::reference).toList();
    }

    @GetMapping("/report-operators")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionReportPermissions.SUPPLEMENT)
    public DirectoryPage<ReferenceOption> historicalUsers(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "0") int offset,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return references(service.historicalUsers(keyword, offset, limit));
    }

    public record ReferenceOption(String id, String code, String name, Integer version,
                                  String description, String status, String departmentId) {
    }

    private static ReferenceOption reference(Label row) {
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return new ReferenceOption(id(row.id()), row.code(), row.name(), row.version(), row.description(), row.status(), id(row.departmentId()));
    }

    private static DirectoryPage<ReferenceOption> references(DirectoryPage<Label> page) {
        return new DirectoryPage<>(page.items().stream().map(BusinessDirectoryController::reference)
                .toList(), page.total(), page.offset(), page.limit());
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
