package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 身份及业务映射尚未验收，只允许有界单页候选读取。不持有业务事务，不写业务表。
 * 单页保留同码候选，不能用显示编码去重或伪装完整同步。无水位和自动重试。
 */
public final class DigiwinE10SyncPreview {
    public enum Kind {WORKORDER, WAREHOUSE}

    public record Capability(boolean supported, boolean writeEnabled, List<String> blockers) {
    }

    public record Candidate(String code, String name, String site, String ownerOrg,
                            String productCode, String unitCode, String processCode,
                            String externalStatus, String plannedQty) {
    }

    public record Preview(String status, String runToken, String provider, String binding,
                          String configurationVersion, int pageNo, int pageSize,
                          int fetched, int created, int updated, int skipped,
                          boolean possiblyMore, List<Candidate> candidates, List<String> blockers) {
    }

    private final DigiwinE10MoQueries queries;
    private final SyncCoordinator coordinator;

    public DigiwinE10SyncPreview(DigiwinE10MoQueries queries, SyncCoordinator coordinator) {
        this.queries = queries;
        this.coordinator = coordinator;
    }

    public Capability capability(Kind kind) {
        var snapshot = FactoryIntegrationContext.required();
        return new Capability(snapshot.mode() == FactoryRuntimeMode.ERP
                && DigiwinE10MoQueries.PROVIDER.equals(snapshot.erpType()), false, blockers(kind));
    }

    public Preview preview(Kind kind, int pageNo, int pageSize) {
        if (pageNo < 1 || pageNo > 10000 || pageSize < 1 || pageSize > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        if (!capability(kind).supported())
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var snapshot = FactoryIntegrationContext.required();
        var run = coordinator.claim(FactoryContext.required(),
                kind == Kind.WORKORDER ? "e10.mo.preview" : "e10.warehouse.preview", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var request = request(pageNo, pageSize);
            var rows = new ArrayList<Candidate>();
            if (kind == Kind.WORKORDER) {
                for (var row : queries.queryMo(request).getRows()) {
                    rows.add(new Candidate(row.getDocNo(), row.getItemName(), text(row.getSource(), "om_site_id"),
                            null, row.getItemNo(), row.getUnitNo(), row.getProcessNo(), row.getStatus(),
                            row.getPlanQty() == null ? null : row.getPlanQty().toPlainString()));
                }
            } else {
                for (var row : queries.queryWarehouse(request).getRows()) {
                    rows.add(new Candidate(text(row.getSource(), "warehouse_no"),
                            text(row.getSource(), "warehouse_name"), text(row.getSource(), "om_site_id"),
                            null, null, null, null, text(row.getSource(), "approve_status"), null));
                }
            }
            if (rows.size() > pageSize) throw new FoundationException(ErrorCode.BAD_REQUEST);
            coordinator.assertOwner(run);
            coordinator.progress(run, rows.size());
            coordinator.succeed(run, null);
            return new Preview("PREVIEW_ONLY", run.runToken(), snapshot.erpType(), snapshot.configurationRef(),
                    snapshot.configurationRevision().toString(), pageNo, pageSize, rows.size(), 0, 0, 0,
                    rows.size() == pageSize, List.copyOf(rows), blockers(kind));
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            throw failure;
        }
    }

    private static E10QueryRequest request(int pageNo, int pageSize) {
        var request = new E10QueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setIsGetSchema(false);
        request.setIsGetCount(false);
        request.setConditions(JsonNodeFactory.instance.arrayNode());
        request.setOrders(JsonNodeFactory.instance.arrayNode());
        return request;
    }

    private static String text(JsonNode source, String key) {
        var value = source.get(key);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private static List<String> blockers(Kind kind) {
        return kind == Kind.WORKORDER
                ? List.of("工单单号的组织/账套唯一性及稳定身份尚未确认", "产品、单位及确切路线版本映射尚未确认",
                "工单业务状态到 MES 状态的转换尚未确认", "分页排序契约尚未确认；本次仅为单页候选，不代表完整同步")
                : List.of("仓库编号与 WAREHOUSE_CODE、组织类型及据点定位字段的部署绑定尚未确认",
                "仓库稳定复合身份及启停状态转换尚未确认", "分页排序契约尚未确认；本次仅为单页候选，不代表完整同步");
    }
}
