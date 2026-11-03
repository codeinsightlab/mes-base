package dev.mesbase.business.basic.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.mesbase.business.basic.dao.*;
import dev.mesbase.business.basic.domain.*;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class BomTreePlans {
    public record Line(String material, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity, int sort,
                       String child) {
    }

    public record Node(String key, @JsonFormat(shape = JsonFormat.Shape.STRING) Long source,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long revision, String product, String code,
                       String version,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal base, String remark, List<Line> lines) {
    }

    public record Plan(String root, List<Node> nodes) {
    }

    public record Change(String key, String action, boolean ancestor, String version, List<Line> before, List<Line> after) {
    }

    public record Preview(String hash, List<Change> changes, Plan original, Plan proposed, int createdVersions,
                          int editedVersions, int insertedLines, int updatedLines, int removedLines) {
    }

    public record Confirm(Plan plan, String hash, UUID requestId) {
    }

    private final MaterialBomService bom;
    private final MaterialBomItemMapper items;
    private final BomPlanReceiptMapper receipts;

    public BomTreePlans(MaterialBomService bom, MaterialBomItemMapper items, BomPlanReceiptMapper receipts) {
        this.bom = bom;
        this.items = items;
        this.receipts = receipts;
    }

    private static void require(boolean ok) {
        if (!ok)
            dev.mesbase.common.validation.ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT, new SafeBusinessMessage("结构、版本号或修订号冲突，请核对后重新预览"));
    }

    private static long id(String value) {
        try {
            long result = Long.parseLong(value);
            require(result > 0 && Long.toString(result).equals(value));
            return result;
        } catch (NumberFormatException e) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
    }

    private static final JsonMapper FINGERPRINT_JSON = JsonMapper.builder().build();

    private static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(FINGERPRINT_JSON.writeValueAsBytes(value)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal plain(BigDecimal n) {
        return new BigDecimal(n.stripTrailingZeros().toPlainString());
    }

    private static Line line(MaterialBomItemEntity e) {
        return new Line(e.getMaterialId()
                .toString(), plain(e.getQuantity()), e.getSort(), Objects.toString(e.getChildBomId(), null));
    }

    private static List<MaterialBomItemEntity> sorted(List<MaterialBomItemEntity> rows) {
        return rows.stream().sorted(Comparator.comparing(MaterialBomItemEntity::getSort)
                .thenComparing(MaterialBomItemEntity::getId)).toList();
    }

    public record Open(Plan plan, Map<String, String> materials, Map<String, String> statuses) {
    }

    public Open open(long root) {
        bom.selectById(root);
        var graph = bom.loadGraph(root);
        var grouped = graph.lines().stream().collect(Collectors.groupingBy(MaterialBomItemEntity::getBomId));
        var plan = new Plan(Long.toString(root), graph.versions().values().stream()
                .sorted(Comparator.comparing(MaterialBomEntity::getId))
                .map(b -> new Node(b.getId().toString(), b.getId(), b.getRevision(), b.getProductId()
                        .toString(), b.getCode(), b.getVersion(), b.getBaseQty(), b.getRemark(),
                        sorted(grouped.getOrDefault(b.getId(), List.of())).stream().map(BomTreePlans::line).toList()))
                .toList());
        return new Open(plan, graph.materials().values().stream().collect(Collectors.toMap(m -> m.getId()
                .toString(), m -> m.getCode() + " · " + m.getName())), graph.versions().values().stream()
                .collect(Collectors.toMap(b -> b.getId().toString(), MaterialBomEntity::getStatus)));
    }

    private List<MaterialBomItemEntity> rows(long bid) {
        var f = new MaterialBomItemExample();
        f.createCriteria().andBomIdEqualTo(bid);
        return sorted(items.selectByExample(f));
    }

    private record State(Map<String, Node> nodes, List<Change> changes,
                         Map<String, String> aliases) {
    }

    private void visit(String key, Map<String, Node> nodes, Set<String> path, Set<String> done, List<String> order,
                       int[] count) {
        var n = nodes.get(key);
        require(n != null);
        if (!path.add(key)) throw new FoundationException(ErrorCode.MATERIAL_BOM_CYCLE);
        if (path.size() > MaterialBomService.MAX_DEPTH) throw new FoundationException(ErrorCode.MATERIAL_BOM_BUDGET);
        var materials = new HashSet<String>();
        for (var l : n.lines()) {
            require(++count[0] <= MaterialBomService.MAX_LINES && l != null && materials.add(l.material()) && !l.material()
                    .equals(n.product()) && l.sort() >= 0 && valid(l.quantity()));
            if (l.child() != null) {
                var c = nodes.get(l.child());
                require(c != null && c.product().equals(l.material()));
                visit(c.key(), nodes, path, done, order, count);
            }
        }
        path.remove(key);
        if (done.add(key)) order.add(key);
    }

    private static boolean valid(BigDecimal v) {
        return v != null && v.signum() > 0 && v.scale() <= 6 && v.precision() - v.scale() <= 14;
    }

    private State analyze(Plan p, boolean lock) {
        require(p != null && p.nodes() != null && !p.nodes().isEmpty() && p.nodes().size() <= MaterialBomService.MAX_BOMS);
        Map<String, Node> nodes = new LinkedHashMap<>();
        Set<Long> materials = new TreeSet<>();
        int lines = 0;
        for (var n : p.nodes()) {
            require(n != null && n.product() != null && n.key() != null && !n.key().isBlank() && n.key()
                    .length() <= 64 && nodes.put(n.key(), n) == null && n.lines() != null && valid(n.base()) && n.code() != null && !n.code()
                    .isBlank() && n.code().length() <= 64 && n.version() != null && !n.version()
                    .isBlank() && n.version().length() <= 32 && (n.remark() == null || n.remark().length() <= 500));
            materials.add(id(n.product()));
            require((lines += n.lines().size()) <= MaterialBomService.MAX_LINES);
            for (var l : n.lines()) {
                require(l != null);
                materials.add(id(l.material()));
            }
        }
        var order = new ArrayList<String>();
        visit(p.root(), nodes, new HashSet<>(), new HashSet<>(), order, new int[1]);
        require(order.size() == nodes.size());
        if (lock) bom.lockMaterials(List.copyOf(materials));
        else bom.checkPlanMaterials(List.copyOf(materials));
        var changed = new HashSet<String>();
        var changes = new ArrayList<Change>();
        var used = new HashMap<List<String>, String>();
        var aliases = new HashMap<String, String>();
        var childIdentities = new HashMap<String, String>();
        var occupied = new HashMap<List<String>,Set<String>>();
        for (var k : order) {
            var n = nodes.get(k);
            String version = n.version();
            List<Line> before = List.of();
            String action = "CREATE";
            boolean ancestor = false;
            if (n.source() != null) {
                var b = lock ? bom.lock(n.source()) : bom.selectById(n.source());
                require(Objects.equals(b.getRevision(), n.revision()) && b.getProductId() == id(n.product()) && b.getCode()
                        .equals(n.code()) && !"DISABLED".equals(b.getStatus()));
                before = rows(b.getId()).stream().map(BomTreePlans::line).toList();
                var mapped = n.lines().stream()
                        .map(l -> new Line(l.material(), plain(l.quantity()), l.sort(), l.child() == null ? null : Objects.toString(nodes.get(l.child())
                                .source(), null))).toList();
                boolean localChange = !before.equals(mapped) || n.base().compareTo(b.getBaseQty()) != 0
                        || !Objects.equals(n.remark(), b.getRemark()) || !n.version().equals(b.getVersion());
                boolean newChild = n.lines().stream().anyMatch(l -> changed.contains(l.child()));
                if (!localChange && !newChild) action = "REUSE";
                else if (k.equals(p.root()) && "DRAFT".equals(b.getStatus()) && !referenced(b.getId())) action = "EDIT";
                else action = "COPY";
                ancestor = action.equals("COPY") && newChild && !localChange;
                if (action.equals("COPY") && version.equals(b.getVersion())) {
                    var existing = occupied.computeIfAbsent(List.of(n.product(), n.code()),
                            unused -> bom.versionsFor(b.getProductId(), b.getCode()));
                    version = suggest(n, existing, used, nodes, childIdentities);
                }
            }
            if (!action.equals("REUSE")) {
                changed.add(k);
                var identity = List.of(n.product(), n.code(), version);
                var prior = used.putIfAbsent(identity, k);
                if (prior != null) {
                    var a = nodes.get(prior);
                    require(sameRevision(n, a, childIdentities));
                    aliases.put(k, prior);
                    action = "SHARE";
                } else bom.unique(n.code(), version, id(n.product()), action.equals("EDIT") ? n.source() : null);
            }
            // Equal unchanged children can arrive under different temporary path keys.
            switch (action) {
                case "REUSE" -> childIdentities.put(k, "source:" + n.source());
                case "SHARE" -> childIdentities.put(k, childIdentities.get(aliases.get(k)));
                default -> childIdentities.put(k, "planned:" + k);
            }
            changes.add(new Change(k, action, ancestor, version, before, n.lines()));
        }
        return new State(nodes, changes, aliases);
    }

    // Keep a numeric suffix when possible (v1 -> v2); arbitrary existing labels stay valid.
    private String suggest(Node n, Set<String> existing, Map<List<String>, String> used,
                           Map<String, Node> nodes, Map<String, String> aliases) {
        var label = n.version();
        int suffix = label.length();
        while (suffix > 0 && label.charAt(suffix - 1) >= '0' && label.charAt(suffix - 1) <= '9') suffix--;
        var prefix = suffix == label.length() ? label + "-r" : label.substring(0, suffix);
        var number = suffix == label.length() ? java.math.BigInteger.ONE : new java.math.BigInteger(label.substring(suffix));
        while (true) {
            number = number.add(java.math.BigInteger.ONE);
            var tail = number.toString();
            if (tail.length() > 32) { prefix = label + "-r"; number = java.math.BigInteger.ONE; continue; }
            var candidate = prefix.substring(0, Math.min(prefix.length(), 32 - tail.length())) + tail;
            if (existing.contains(candidate)) continue;
            var prior = used.get(List.of(n.product(), n.code(), candidate));
            if (prior == null || sameRevision(n, nodes.get(prior), aliases)) return candidate;
        }
    }

    private boolean sameRevision(Node n, Node other, Map<String, String> aliases) {
        return Objects.equals(n.source(), other.source()) && n.base().compareTo(other.base()) == 0
                && Objects.equals(n.remark(), other.remark()) && canonical(n, aliases).equals(canonical(other, aliases));
    }

    private boolean referenced(long id) {
        var f = new MaterialBomItemExample();
        f.createCriteria().andChildBomIdEqualTo(id);
        return items.countByExample(f) > 0;
    }

    private List<Line> canonical(Node n, Map<String, String> aliases) {
        return n.lines().stream()
                .map(l -> new Line(l.material(), plain(l.quantity()), l.sort(), aliases.getOrDefault(l.child(), l.child())))
                .toList();
    }

    private Preview describe(Plan p, State s) {
        var root = s.nodes.get(p.root());
        Plan original = root.source() == null ? new Plan(p.root(), List.of()) : open(root.source()).plan();
        int creates = 0, edits = 0, inserts = 0, updates = 0, deletes = 0;
        for (var c : s.changes) {
            if (c.action().equals("REUSE") || c.action().equals("SHARE")) continue;
            if (!c.action().equals("EDIT")) {
                creates++;
                inserts += c.after().size();
                continue;
            }
            edits++;
            var prior = c.before().stream().collect(Collectors.toMap(Line::material, l -> l));
            for (var l : c.after()) {
                var old = prior.remove(l.material());
                if (old == null) inserts++;
                else {
                    var child = l.child() == null ? null : s.nodes.get(l.child());
                    boolean sameChild = child == null ? old.child() == null : Objects.equals(old.child(), Objects.toString(child.source(), null))
                            && s.changes.stream()
                            .anyMatch(ch -> ch.key().equals(child.key()) && ch.action().equals("REUSE"));
                    if (!sameChild || old.sort() != l.sort() || old.quantity().compareTo(l.quantity()) != 0) updates++;
                }
            }
            deletes += prior.size();
        }
        var proposed = new Plan(p.root(), s.changes.stream().map(change -> {
            var n = s.nodes.get(change.key());
            return new Node(n.key(), n.source(), n.revision(), n.product(), n.code(), change.version(),
                    n.base(), n.remark(), n.lines());
        }).toList());
        return new Preview(hash(List.of(dev.mesbase.platform.execution.FactoryContext.required().factoryId()
                .toString(), p, original, s.changes)), s.changes, original, proposed, creates, edits, inserts, updates, deletes);
    }

    public Preview preview(Plan p) {
        return describe(p, analyze(p, false));
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public String confirm(Confirm c) {
        require(c != null && c.plan() != null && c.requestId() != null && c.hash() != null && c.hash().length() == 64);
        String key = c.requestId().toString(), digest = hash(List.of(c.plan(), c.hash()));
        long actor = SecurityUtils.getUserId();
        receipts.claim(key, actor, digest);
        var existing = receipts.replay(key, actor, digest);
        if (existing != null) return existing.toString();
        var s = analyze(c.plan(), true);
        require(describe(c.plan(), s).hash().equals(c.hash()));
        var ids = new HashMap<String, Long>();
        for (var change : s.changes) {
            var n = s.nodes.get(change.key());
            long target;
            switch (change.action()) {
                case "REUSE" -> target = n.source();
                case "SHARE" -> target = ids.get(s.aliases.get(n.key()));
                default -> {
                    if (change.action().equals("CREATE"))
                        target = bom.insert(new MaterialBomCreateCommand(n.code(), n.version(), id(n.product()), n.base(), n.remark()))
                                .getId();
                    else {
                        var b = change.action()
                                .equals("COPY") ? bom.copy(n.source(), new MaterialBomCopyCommand(change.version(), n.revision())) : bom.selectById(n.source());
                        target = b.getId();
                        bom.updateById(target, new MaterialBomUpdateCommand(n.code(), change.version(), n.base(), n.remark(), b.getRevision()));
                    }
                    var old = rows(target);
                    var want = n.lines().stream().map(Line::material).collect(Collectors.toSet());
                    var drop = old.stream().filter(l -> !want.contains(l.getMaterialId().toString()))
                            .map(MaterialBomItemEntity::getId).toList();
                    for (int i = 0; i < drop.size(); i += 100)
                        bom.deleteItems(drop.subList(i, Math.min(i + 100, drop.size())));
                    var byMat = old.stream().collect(Collectors.toMap(MaterialBomItemEntity::getMaterialId, l -> l));
                    for (var l : n.lines()) {
                        var prev = byMat.get(id(l.material()));
                        Long child = ids.get(l.child());
                        if (prev == null)
                            bom.insertItem(new MaterialBomItemCreateCommand(target, id(l.material()), l.quantity(), child, l.sort()));
                        else if (prev.getQuantity()
                                .compareTo(l.quantity()) != 0 || !Objects.equals(child, prev.getChildBomId()) || prev.getSort() != l.sort())
                            bom.updateItem(prev.getId(), new MaterialBomItemUpdateCommand(id(l.material()), l.quantity(), child, l.sort()));
                    }
                }
            }
            ids.put(n.key(), target);
        }
        long root = ids.get(c.plan().root());
        require(receipts.finish(key, actor, digest, root) == 1);
        return Long.toString(root);
    }
}
