package dev.mesbase.business.production.service;

import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionQueryDto;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import java.util.List;
import dev.mesbase.business.production.domain.MesProductionExecutionPage;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.WorkorderExample;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cached execution reads plus batch display references; does not change commands or execution state.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionExecutionQueryService {
    public record Display(MesProductionExecutionEntity record, DirectoryLabel routing, String createByName,
                          String updateByName, String workorderStatus) {
    }

    public record References(Map<Long, DirectoryLabel> routings, Map<Long, DirectoryLabel> users, Map<Long, String> workorderStatuses) { }

    public record Search(MesProductionExecutionPage records, References references) { }


    private final IMesProductionExecutionService executions;
    private final BusinessDirectoryService references;
    private final WorkorderMapper orders;

    public ProductionExecutionQueryService(IMesProductionExecutionService executions,
                                           BusinessDirectoryService references, WorkorderMapper orders) {
        this.orders = orders;
        this.executions = executions;
        this.references = references;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Search page(MesProductionExecutionQueryDto query) {
        var page = executions.selectPage(query); // Retains the existing guarded page cache.
        return new Search(page, references(page.items()));
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Display detail(Long id) {
        return describe(List.of(executions.selectById(id))).getFirst();
    }

    private List<Display> describe(List<MesProductionExecutionEntity> rows) {
        var data = references(rows);
        return rows.stream().map(row -> new Display(row, data.routings().get(row.getRoutingId()),
                name(data.users(), row.getCreateBy()), name(data.users(), row.getUpdateBy()),
                data.workorderStatuses().get(row.getWorkorderId()))).toList();
    }

    private References references(List<MesProductionExecutionEntity> rows) {
        var filter = new WorkorderExample();
        var ids = rows.stream().map(MesProductionExecutionEntity::getWorkorderId).distinct().toList();
        if (ids.isEmpty()) return new References(Map.of(), Map.of(), Map.of());
        filter.createCriteria().andIdIn(ids);
        var statuses = orders.selectByExample(filter).stream()
                .collect(Collectors.toMap(row -> row.getId(), row -> row.getStatus()));
        var routings = references.routingLabels(rows.stream().map(MesProductionExecutionEntity::getRoutingId).toList());
        var users = references.userLabels(rows.stream().flatMap(row -> Stream.of(row.getCreateBy(), row.getUpdateBy()))
                .map(ProductionExecutionQueryService::userId).filter(id -> id != null).toList());
        return new References(routings, users, statuses);
    }

    public static String name(Map<Long, DirectoryLabel> users, String value) {
        var id = userId(value);
        if (id == null) return null;
        var label = users.get(id);
        if (label == null) return null;
        return label.name();
    }

    private static Long userId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
