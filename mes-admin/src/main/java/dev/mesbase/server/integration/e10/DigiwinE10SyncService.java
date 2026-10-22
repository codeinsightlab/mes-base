package dev.mesbase.server.integration.e10;

import dev.mesbase.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import com.lixin.capability.digiwin.e10.response.E10ObservedRecord;
import java.util.HashSet;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.server.integration.e10.DigiwinE10SyncMapping.Item;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded full-scope scan: at most 5 pages / 100 candidates, no retries, no incremental watermark.
 */
public class DigiwinE10SyncService {
    private static final Logger LOG = LoggerFactory.getLogger(DigiwinE10SyncService.class);

    public record Result(String status, String factoryId, String provider, String binding, String configurationVersion,
                         String runToken, int fetched, int duplicates, int creatable, int updatable, int unchanged,
                         int conflicts, int blocked, int created, int updated, List<Item> items, String failureReason) {
    }

    private record Candidate(JsonNode raw, String blocker) {
    }

    private final DigiwinE10MoQueries queries;
    private final FactoryOapiService configurations;
    private final DigiwinE10SyncMapping mapping;
    private final DigiwinE10SyncWriter writer;
    private final SyncCoordinator coordinator;

    public DigiwinE10SyncService(DigiwinE10MoQueries queries, FactoryOapiService configurations,
                                 DigiwinE10SyncMapping mapping, DigiwinE10SyncWriter writer,
                                 SyncCoordinator coordinator) {
        this.queries = queries;
        this.configurations = configurations;
        this.mapping = mapping;
        this.writer = writer;
        this.coordinator = coordinator;
    }

    public DigiwinE10SyncPreview.Capability capability(Kind kind) {
        var snapshot = FactoryIntegrationContext.required();
        if (snapshot.mode() != FactoryRuntimeMode.ERP || !DigiwinE10MoQueries.PROVIDER.equals(snapshot.erpType()))
            return new DigiwinE10SyncPreview.Capability(false, false, List.of("当前工厂未绑定 E10"));
        var contract = contract();
        var rule = rule(contract, kind);
        boolean enabled = contract != null && contract.commitEnabled() && rule != null;
        return new DigiwinE10SyncPreview.Capability(true, enabled, enabled ? List.of()
                : List.of("正式提交未开放：需在工厂连接中保存经目标环境核实的身份、组织、状态及排序契约"));
    }

    public Result execute(Kind kind, boolean commit, int pageSize, int maxPages) {
        if (pageSize < 1 || pageSize > 20 || maxPages < 1 || maxPages > 5)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        if (!capability(kind).supported() || commit && !capability(kind).writeEnabled())
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var contract = contract();
        var rule = rule(contract, kind);
        var run = coordinator.claim(FactoryContext.required(), commit
                ? kind == Kind.WORKORDER ? "workorder.erp" : "codegen.wm_warehouse"
                : kind == Kind.WORKORDER ? "e10.mo.dry-run" : "e10.warehouse.dry-run", Duration.ofMinutes(5));
        var items = new ArrayList<Item>();
        int created = 0, updated = 0, duplicates = 0, fetched = 0;
        try {
            var candidates = new ArrayList<Candidate>();
            boolean complete = false;
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            for (int page = 1; page <= (rule == null ? 1 : maxPages); page++) {
                budget(deadline);
                coordinator.progress(run, 0);
                contract();
                var request = request(rule, page, pageSize);
                List<JsonNode> rows = kind == Kind.WORKORDER
                        ? queries.queryMo(request).getRows().stream().map(x -> x.getSource()).toList()
                        : queries.queryWarehouse(request).getRows().stream().map(x -> x.getSource()).toList();
                if (rows.size() > pageSize) throw new FoundationException(ErrorCode.BAD_REQUEST);
                fetched += rows.size();
                for (var row : rows) {
                    budget(deadline);
                    coordinator.progress(run, 0);
                    candidates.add(read(kind, row, rule));
                }
                if (rows.size() < pageSize) {
                    complete = true;
                    break;
                }
            }
            // Fetch all pages before any write. Never commit an incomplete bounded scan.
            var identities = new HashMap<String, JsonNode>();
            var conflicting = new HashSet<String>();
            var codes = new HashMap<String, String>();
            for (var candidate : candidates) {
                String id = E10ExternalIdentity.resolve(candidate.raw(), rule);
                if (id == null) continue;
                var old = identities.putIfAbsent(id, candidate.raw());
                if (old != null && !old.equals(candidate.raw())) conflicting.add(id);
                String code = E10ExternalIdentity.text(candidate.raw(), kind == Kind.WORKORDER ? "/doc_no" : "/warehouse_no");
                var prior = codes.putIfAbsent(code, id);
                if (prior != null && !prior.equals(id)) {
                    conflicting.add(prior);
                    conflicting.add(id);
                }
            }
            var seen = new HashSet<String>();
            for (var candidate : candidates) {
                coordinator.assertOwner(run);
                contract();
                String id = E10ExternalIdentity.resolve(candidate.raw(), rule);
                if (id != null && !seen.add(id) && !conflicting.contains(id)) {
                    duplicates++;
                    continue;
                }
                Item item = mapping.prepare(kind, candidate.raw(), rule).item();
                if (id != null && conflicting.contains(id))
                    item = block(item, "CONFLICT", "同一身份内容不一致或同编码不同身份");
                else if (candidate.blocker() != null) item = block(item, "UNSUPPORTED", candidate.blocker());
                else if (!complete)
                    item = block(item, "UNSUPPORTED", "分页范围超过本次上限，缩小受控查询范围后重试；未执行写入");
                if (commit && writable(item)) {
                    try {
                        item = writer.write(kind, candidate.raw(), rule, run);
                        if ("CREATE".equals(item.action())) created++;
                        if ("UPDATE".equals(item.action())) updated++;
                    } catch (FoundationException conflict) {
                        if (conflict.code() == ErrorCode.SYNC_EXECUTION_LOST) throw conflict;
                        LOG.warn("E10 sync candidate rejected run={}", run.runToken(), conflict);
                        item = block(item, "CONFLICT", conflict.code().name());
                    }
                }
                items.add(item);
                coordinator.progress(run, items.size());
            }
            long rejected = items.stream().filter(x -> !writable(x)).count();
            String status = !commit ? "DRY_RUN" : rejected == 0 ? "COMMITTED"
                    : items.stream().anyMatch(DigiwinE10SyncService::writable) ? "PARTIAL_SUCCESS" : "FAILED";
            LOG.info("E10 sync completed run={} kind={} status={} fetched={} created={} updated={} rejected={} duplicates={}",
                    run.runToken(), kind, status, fetched, created, updated, rejected, duplicates);
            if (commit && rejected > 0) coordinator.fail(run);
            else coordinator.succeed(run, null);
            return result(status, run, fetched, duplicates, created, updated, items, null);
        } catch (RuntimeException failure) {
            LOG.error("E10 sync failed run={} committedCreates={} committedUpdates={}", run.runToken(), created, updated, failure);
            try {
                coordinator.fail(run);
            } catch (RuntimeException stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            return result(created + updated > 0 ? "PARTIAL_SUCCESS" : "FAILED", run, fetched, duplicates,
                    created, updated, items, "执行中断，已提交数量见结果；按运行标识查阅后台日志，未处理记录不视为成功");
        }
    }

    private Candidate read(Kind kind, JsonNode query, E10SyncContract.Rule rule) {
        if (rule == null || !rule.readRequired()) return new Candidate(query, null);
        List<? extends E10ObservedRecord> records;
        if (kind == Kind.WORKORDER) {
            String code = E10ExternalIdentity.text(query, "/doc_no");
            if (StringUtils.isBlank(code)) return new Candidate(query, "READ_KEY_INCOMPLETE: 缺少工单定位值");
            records = queries.readMo(code).getRecords();
        } else {
            String code = E10ExternalIdentity.text(query, rule.readCodePointer());
            String owner = E10ExternalIdentity.text(query, rule.readOwnerPointer());
            String plant = E10ExternalIdentity.text(query, rule.readPlantPointer());
            if (StringUtils.isBlank(code) || StringUtils.isBlank(owner) || StringUtils.isBlank(plant))
                return new Candidate(query, "READ_KEY_INCOMPLETE: 缺少仓库真实复合定位值");
            records = queries.readWarehouse(code, owner, plant).getRecords();
        }
        if (records.size() != 1) return new Candidate(query, "Read 未返回唯一记录");
        JsonNode read = records.getFirst().getSource();
        String codePointer = kind == Kind.WORKORDER ? "/doc_no" : "/warehouse_no";
        String queryIdentity = E10ExternalIdentity.resolve(query, rule);
        if (!Objects.equals(E10ExternalIdentity.text(query, codePointer), E10ExternalIdentity.text(read, codePointer))
                || queryIdentity != null && !queryIdentity.equals(E10ExternalIdentity.resolve(read, rule)))
            return new Candidate(query, "Read 与 Query 身份不一致；禁止合并字段");
        return new Candidate(read, null);
    }

    private E10SyncContract contract() {
        var snapshot = FactoryIntegrationContext.required();
        var current = configurations.connection(snapshot.factoryId().value());
        if (snapshot.mode() != FactoryRuntimeMode.ERP || !DigiwinE10MoQueries.PROVIDER.equals(snapshot.erpType())
                || current == null || !snapshot.factoryId().value().equals(current.factoryId())
                || !Objects.equals(snapshot.configurationRef(), current.reference())
                || !Objects.equals(snapshot.configurationRevision(), current.revision()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var contract = current.settings().syncContract();
        if (contract != null) contract.validate();
        return contract;
    }

    private static E10SyncContract.Rule rule(E10SyncContract contract, Kind kind) {
        if (contract == null) return null;
        return kind == Kind.WORKORDER ? contract.workorder() : contract.warehouse();
    }

    private static void budget(long deadline) {
        if (System.nanoTime() > deadline) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static boolean writable(Item item) {
        return List.of("CREATE", "UPDATE", "UNCHANGED").contains(item.action());
    }

    private static Item block(Item item, String action, String reason) {
        var reasons = new ArrayList<>(item.reasons());
        reasons.add(reason);
        return new Item(item.code(), item.externalId(), action, List.copyOf(reasons), item.productId(), item.routingId(), item.quantity(), item.unit());
    }

    private static E10QueryRequest request(E10SyncContract.Rule rule, int page, int size) {
        var request = new E10QueryRequest();
        request.setPageNo(page);
        request.setPageSize(size);
        request.setIsGetSchema(false);
        request.setIsGetCount(false);
        request.setConditions(rule == null ? JsonNodeFactory.instance.arrayNode() : rule.conditions());
        request.setOrders(rule == null ? JsonNodeFactory.instance.arrayNode() : rule.orders());
        return request;
    }

    private static Result result(String status, SyncCoordinator.Run run, int fetched, int duplicates, int created,
                                 int updated, List<Item> items, String failureReason) {
        var snapshot = FactoryIntegrationContext.required();
        int create = count(items, "CREATE"), update = count(items, "UPDATE"), unchanged = count(items, "UNCHANGED"), conflicts = count(items, "CONFLICT");
        return new Result(status, run.scope().factoryId().value(), snapshot.erpType(), snapshot.configurationRef(),
                snapshot.configurationRevision()
                        .toString(), run.runToken(), fetched, duplicates, create, update, unchanged,
                conflicts, items.size() - create - update - unchanged - conflicts, created, updated, List.copyOf(items), failureReason);
    }

    private static int count(List<Item> items, String action) {
        return (int) items.stream().filter(x -> action.equals(x.action())).count();
    }
}
