package dev.mesbase.business.directory.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.domain.DirectoryPage;
import dev.mesbase.business.directory.domain.DirectoryQuery;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.controller.ProductionReportPermissions;
import dev.mesbase.business.production.controller.RoutingPermissions;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/directory")
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class BusinessDirectoryController {
    public static final String PERSONNEL_READ = "business:directory:personnel:read";
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

    private static ReferenceOption reference(DirectoryLabel row) {
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return BeanConversions.convert(row, ReferenceOption.class);
    }

    private static DirectoryPage<ReferenceOption> references(DirectoryPage<DirectoryLabel> page) {
        return BeanConversions.convertPage(page, BusinessDirectoryController::reference, DirectoryPage::new);
    }

    private static DirectoryOption personnelView(SysUser row) {
        return new DirectoryOption(id(row.getUserId()), row.getNickName(), row.getUserName(),
                null, id(row.getDeptId()));
    }

    private static String id(Long value) {
        return Objects.toString(value, null);
    }

    private static <T> DirectoryPage<DirectoryOption> view(DirectoryPage<T> page,
                                                           Function<T, DirectoryOption> convert) {
        return BeanConversions.convertPage(page, convert, DirectoryPage::new);
    }
}
