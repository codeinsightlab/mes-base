package dev.mesbase.business.wm.service;

import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehousePage;
import dev.mesbase.business.wm.domain.WmWarehouseQueryDto;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouse reads retain the guarded entity cache; user names are resolved in one batch.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WarehouseQueryService {
    public record Search(WmWarehousePage records, Map<Long, DirectoryLabel> users) {
    }

    public record Detail(WmWarehouseEntity record, Map<Long, DirectoryLabel> users) {
    }

    private final IWmWarehouseService warehouses;
    private final BusinessDirectoryService directory;

    public WarehouseQueryService(IWmWarehouseService warehouses, BusinessDirectoryService directory) {
        this.warehouses = warehouses;
        this.directory = directory;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Search page(WmWarehouseQueryDto query) {
        var page = warehouses.selectPage(query);
        return new Search(page, users(page.items()));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(Long id) {
        var row = warehouses.selectById(id);
        return new Detail(row, users(List.of(row)));
    }

    private Map<Long, DirectoryLabel> users(List<WmWarehouseEntity> rows) {
        var ids = rows.stream().flatMap(row -> Stream.of(row.getCreateBy(), row.getUpdateBy()))
                .map(WarehouseQueryService::userId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return directory.userLabels(ids);
    }

    public static String name(Map<Long, DirectoryLabel> users, String value) {
        if (value == null || value.isBlank()) return null;
        Long id = userId(value);
        // Existing textual audit actors already identify the account; never turn unresolved numeric IDs into names.
        if (id == null) {
            if (value.matches("[0-9]+")) return "未知用户";
            return value;
        }
        var user = users.get(id);
        if (user == null) return "未知用户";
        return Optional.ofNullable(user.name()).filter(name -> !name.isBlank()).orElse(user.code());
    }

    private static Long userId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException failure) {
            return null;
        }
    }
}
