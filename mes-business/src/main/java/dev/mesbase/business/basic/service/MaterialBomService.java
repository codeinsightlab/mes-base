package dev.mesbase.business.basic.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialBomItemMapper;
import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialBomCreateCommand;
import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.business.basic.domain.MaterialBomExample;
import dev.mesbase.business.basic.domain.MaterialBomGraph;
import dev.mesbase.business.basic.domain.MaterialBomItemCreateCommand;
import dev.mesbase.business.basic.domain.MaterialBomItemEntity;
import dev.mesbase.business.basic.domain.MaterialBomItemExample;
import dev.mesbase.business.basic.domain.MaterialBomItemPage;
import dev.mesbase.business.basic.domain.MaterialBomItemRow;
import dev.mesbase.business.basic.domain.MaterialBomItemQueryDto;
import dev.mesbase.business.basic.domain.MaterialBomItemUpdateCommand;
import dev.mesbase.business.basic.domain.MaterialBomPage;
import dev.mesbase.business.basic.domain.MaterialBomQueryDto;
import dev.mesbase.business.basic.domain.MaterialBomStatus;
import dev.mesbase.business.basic.domain.MaterialBomUpdateCommand;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned BOM aggregate. Generated mappers remain the only table access implementation.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class MaterialBomService {
    private static final int MAX_BOMS = 2000;
    private static final int MAX_LINES = 10000;
    private static final int READ_BATCH_SIZE = 500;
    private static final int MAX_DEPTH = 100;
    private final MaterialBomMapper boms;
    private final MaterialBomItemMapper items;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;

    public MaterialBomService(MaterialBomMapper boms, MaterialBomItemMapper items, MaterialMapper materials,
                              UnitMeasureMapper units) {
        this.boms = boms;
        this.items = items;
        this.materials = materials;
        this.units = units;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialBomPage selectPage(MaterialBomQueryDto q) {
        int offset = q.cacheOffset(), limit = q.cacheLimit();
        ParameterChecks.paging(offset, limit);
        var filter = new MaterialBomExample();
        var c = filter.createCriteria();
        if (q.productId() != null) c.andProductIdEqualTo(q.productId());
        if (q.code() != null) c.andCodeLike("%" + q.code() + "%");
        if (q.version() != null) c.andVersionEqualTo(q.version());
        if (q.status() != null) {
            if (MaterialBomStatus.from(q.status()) == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            c.andStatusEqualTo(q.status());
        }
        filter.setOrderByClause("id DESC");
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = boms.selectByExample(filter);
            return new MaterialBomPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialBomEntity selectById(Long id) {
        return require(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialBomEntity selectDefault(Long productId) {
        if (productId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var product = materials.selectByPrimaryKey(productId);
        if (product == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (!EnableStatus.ENABLED.getValue().equals(product.getStatus())) conflict();
        return boms.selectDefaultByProductId(productId);
    }

    public MaterialBomEntity insert(MaterialBomCreateCommand c) {
        var product = lockMaterial(c.productId());
        decimal(c.baseQty());
        ProductionQuantityInput.check(c.baseQty());
        unique(c.code(), c.version(), product.getId(), null);
        var row = new MaterialBomEntity();
        row.setCode(c.code());
        row.setVersion(c.version());
        row.setProductId(product.getId());
        row.setBaseQty(c.baseQty());
        row.setUnitId(product.getUnitId());
        row.setStatus(MaterialBomStatus.DRAFT.getValue());
        row.setDefaultVersion(false);
        row.setRevision(1L);
        row.setRemark(c.remark());
        row.setCreatedBy(SecurityUtils.getUserId());
        row.setCreatedAt(LocalDateTime.now());
        changed(boms.insert(row));
        return row;
    }

    public MaterialBomEntity updateById(Long id, MaterialBomUpdateCommand c) {
        var row = editable(id);
        if (!Objects.equals(row.getRevision(), c.revision())) conflict();
        decimal(c.baseQty());
        ProductionQuantityInput.check(c.baseQty());
        unique(c.code(), c.version(), row.getProductId(), id);
        row.setCode(c.code());
        row.setVersion(c.version());
        row.setBaseQty(c.baseQty());
        row.setRemark(c.remark());
        bump(row);
        return row;
    }

    public void deleteByIds(List<Long> ids) {
        checkIds(ids);
        lockMaterials(ids.stream().map(id -> require(id).getProductId()).toList());
        // Lock a stable order and reject referenced versions. Deleting a draft does not delete its components implicitly.
        for (var id : ids.stream().sorted().toList()) {
            editable(id);
            var f = new MaterialBomItemExample();
            f.createCriteria().andChildBomIdEqualTo(id);
            if (items.countByExample(f) > 0) conflict();
            f = new MaterialBomItemExample();
            f.createCriteria().andBomIdEqualTo(id);
            if (items.countByExample(f) > 0) conflict();
            changed(boms.deleteByPrimaryKey(id));
        }
    }

    public MaterialBomEntity publish(Long id, long revision) {
        var row = editable(id);
        if (row.getRevision() != revision) conflict();
        graph(id, row.getBaseQty(), true);
        row.setStatus(MaterialBomStatus.PUBLISHED.getValue());
        row.setPublishedAt(LocalDateTime.now());
        bump(row);
        return row;
    }

    public MaterialBomEntity disable(Long id, long revision) {
        var row = lock(id);
        if (row.getRevision() != revision || !MaterialBomStatus.PUBLISHED.getValue().equals(row.getStatus()))
            conflict();
        // Parent versions remain readable; a disabled child prevents new published usage, not historical reads.
        row.setDefaultVersion(false);
        row.setStatus(MaterialBomStatus.DISABLED.getValue());
        bump(row);
        return row;
    }

    public MaterialBomEntity setDefault(Long id, long revision) {
        var selected = lock(id);
        if (selected.getRevision() != revision
                || !MaterialBomStatus.PUBLISHED.getValue().equals(selected.getStatus())) conflict();
        if (Boolean.TRUE.equals(selected.getDefaultVersion())) return selected;
        var previous = boms.selectDefaultByProductId(selected.getProductId());
        if (previous != null) {
            previous.setDefaultVersion(false);
            changed(boms.updateByPrimaryKey(previous));
        }
        selected.setDefaultVersion(true);
        changed(boms.updateByPrimaryKey(selected));
        return selected;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialBomItemPage itemPage(MaterialBomItemQueryDto q) {
        if (q.bomId() == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        require(q.bomId());
        ParameterChecks.paging(q.cacheOffset(), q.cacheLimit());
        var f = new MaterialBomItemExample();
        f.createCriteria().andBomIdEqualTo(q.bomId());
        f.setOrderByClause("sort ASC,id ASC");
        try (var page = PageHelper.offsetPage(q.cacheOffset(), q.cacheLimit())) {
            var rows = items.selectByExample(f);
            long total = page.getTotal();
            if (rows.isEmpty()) return new MaterialBomItemPage(List.of(), total, q.cacheOffset(), q.cacheLimit());
            var materialFilter = new MaterialExample();
            materialFilter.createCriteria()
                    .andIdIn(rows.stream().map(MaterialBomItemEntity::getMaterialId).distinct().toList());
            var materialMap = materials.selectByExample(materialFilter).stream()
                    .collect(Collectors.toMap(MaterialEntity::getId, Function.identity()));
            var unitFilter = new UnitMeasureExample();
            unitFilter.createCriteria()
                    .andIdIn(rows.stream().map(MaterialBomItemEntity::getUnitId).distinct().toList());
            var unitMap = units.selectByExample(unitFilter).stream()
                    .collect(Collectors.toMap(UnitMeasureEntity::getId, Function.identity()));
            var result = rows.stream()
                    .map(row -> MaterialBomItemRow.of(row, materialMap.get(row.getMaterialId()), unitMap.get(row.getUnitId())))
                    .toList();
            return new MaterialBomItemPage(result, total, q.cacheOffset(), q.cacheLimit());
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialBomItemEntity item(Long id) {
        var row = items.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    public MaterialBomItemEntity insertItem(MaterialBomItemCreateCommand c) {
        lockMaterials(List.of(require(c.bomId()).getProductId(), c.materialId()));
        var parent = editable(c.bomId());
        var row = new MaterialBomItemEntity();
        row.setBomId(parent.getId());
        fill(row, c.materialId(), c.quantity(), c.childBomId(), c.sort(), parent);
        changed(items.insert(row));
        bump(parent);
        return row;
    }

    public MaterialBomItemEntity updateItem(Long id, MaterialBomItemUpdateCommand c) {
        var original = item(id);
        lockMaterials(List.of(require(original.getBomId()).getProductId(), original.getMaterialId(), c.materialId()));
        var parent = editable(original.getBomId());
        var row = item(id);
        fill(row, c.materialId(), c.quantity(), c.childBomId(), c.sort(), parent);
        changed(items.updateByPrimaryKey(row));
        bump(parent);
        return row;
    }

    public void deleteItems(List<Long> ids) {
        checkIds(ids);
        var rows = ids.stream().map(this::item).toList();
        lockMaterials(rows.stream().map(row -> require(row.getBomId()).getProductId()).toList());
        var parents = rows.stream().map(MaterialBomItemEntity::getBomId).distinct().sorted().map(this::editable)
                .toList();
        for (var row : rows) changed(items.deleteByPrimaryKey(row.getId()));
        parents.forEach(this::bump);
    }

    private void fill(MaterialBomItemEntity row, Long materialId, BigDecimal quantity, Long child, Integer sort,
                      MaterialBomEntity parent) {
        decimal(quantity);
        ProductionQuantityInput.check(quantity);
        if (sort == null || sort < 0 || Objects.equals(materialId, parent.getProductId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var material = lockMaterial(materialId);
        var f = new MaterialBomItemExample();
        var c = f.createCriteria().andBomIdEqualTo(parent.getId()).andMaterialIdEqualTo(materialId);
        if (row.getId() != null) c.andIdNotEqualTo(row.getId());
        if (items.countByExample(f) > 0) conflict();
        if (child != null) {
            var version = boms.selectByPrimaryKeyForUpdate(child);
            if (version == null || !Objects.equals(version.getProductId(), materialId)
                    || !MaterialBomStatus.PUBLISHED.getValue()
                    .equals(version.getStatus()) || Objects.equals(child, parent.getId())) conflict();
        }
        row.setMaterialId(materialId);
        row.setUnitId(material.getUnitId());
        row.setQuantity(quantity);
        row.setChildBomId(child);
        row.setSort(sort);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialBomGraph graph(Long id, BigDecimal outputQty) {
        return graph(id, outputQty, false);
    }

    private MaterialBomGraph graph(Long id, BigDecimal outputQty, boolean publishing) {
        decimal(outputQty);
        require(id);
        // Load only explicit references reachable from this root. Each bounded frontier is read
        // in batches; shared subassemblies are loaded once, while walk preserves every path.
        var versions = new HashMap<Long, MaterialBomEntity>();
        var lines = new ArrayList<MaterialBomItemEntity>();
        var requested = new HashSet<Long>();
        var frontier = List.of(id);
        int level = 0;
        while (!frontier.isEmpty()) {
            if (level++ >= MAX_DEPTH || requested.size() + frontier.size() > MAX_BOMS)
                ParameterChecks.reject(ErrorCode.MATERIAL_BOM_BUDGET);
            requested.addAll(frontier);
            var next = new HashSet<Long>();
            for (int start = 0; start < frontier.size(); start += READ_BATCH_SIZE) {
                var batch = frontier.subList(start, Math.min(frontier.size(), start + READ_BATCH_SIZE));
                var versionFilter = new MaterialBomExample();
                versionFilter.createCriteria().andIdIn(batch);
                for (var version : boms.selectByExample(versionFilter)) versions.put(version.getId(), version);
                var lineFilter = new MaterialBomItemExample();
                lineFilter.createCriteria().andBomIdIn(batch);
                // Limit the result as well as the IN list, so corrupt high-fanout data stays bounded.
                try (var page = PageHelper.offsetPage(0, MAX_LINES - lines.size() + 1, false)) {
                    var rows = items.selectByExample(lineFilter);
                    lines.addAll(rows);
                    if (lines.size() > MAX_LINES) ParameterChecks.reject(ErrorCode.MATERIAL_BOM_BUDGET);
                    for (var line : rows) {
                        if (line.getChildBomId() != null && !requested.contains(line.getChildBomId()))
                            next.add(line.getChildBomId());
                    }
                }
            }
            frontier = next.stream().sorted().toList();
        }
        var materialIds = new HashSet<Long>();
        versions.values().forEach(v -> materialIds.add(v.getProductId()));
        lines.forEach(l -> materialIds.add(l.getMaterialId()));
        var materialFilter = new MaterialExample();
        materialFilter.createCriteria().andIdIn(new ArrayList<>(materialIds));
        var materialMap = materials.selectByExample(materialFilter).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, Function.identity()));
        var unitIds = materialMap.values().stream().map(MaterialEntity::getUnitId).filter(Objects::nonNull).distinct()
                .toList();
        if (unitIds.isEmpty()) conflict();
        var unitFilter = new UnitMeasureExample();
        unitFilter.createCriteria().andIdIn(unitIds);
        var unitMap = units.selectByExample(unitFilter).stream()
                .collect(Collectors.toMap(UnitMeasureEntity::getId, Function.identity()));
        var grouped = lines.stream().collect(Collectors.groupingBy(MaterialBomItemEntity::getBomId));
        var totals = new LinkedHashMap<Long, MaterialBomGraph.Total>();
        var nodes = walk(id, outputQty, "", new HashSet<>(), versions, grouped, materialMap, unitMap, totals, publishing, 0, new int[]{0});
        var rounded = totals.values().stream()
                .map(t -> new MaterialBomGraph.Total(t.materialId(), t.code(), t.name(), t.quantity()
                        .setScale(6, RoundingMode.HALF_UP), t.unitName())).toList();
        return new MaterialBomGraph(id, outputQty, nodes, rounded);
    }

    private List<MaterialBomGraph.Node> walk(Long id, BigDecimal output, String path, Set<Long> ancestors,
                                             Map<Long, MaterialBomEntity> versions,
                                             Map<Long, List<MaterialBomItemEntity>> grouped,
                                             Map<Long, MaterialEntity> materialMap,
                                             Map<Long, UnitMeasureEntity> unitMap,
                                             Map<Long, MaterialBomGraph.Total> totals, boolean publishing, int depth,
                                             int[] visited) {
        if (depth >= MAX_DEPTH) ParameterChecks.reject(ErrorCode.MATERIAL_BOM_BUDGET);
        if (!ancestors.add(id)) ParameterChecks.reject(ErrorCode.MATERIAL_BOM_CYCLE);
        var version = versions.get(id);
        if (version == null) conflict();
        decimal(version.getBaseQty());
        var product = materialMap.get(version.getProductId());
        if (product == null || !Objects.equals(product.getUnitId(), version.getUnitId())) conflict();
        if (publishing && (!EnableStatus.ENABLED.getValue().equals(product.getStatus())
                || depth > 0 && !MaterialBomStatus.PUBLISHED.getValue().equals(version.getStatus()))) conflict();
        var rows = grouped.getOrDefault(id, List.of()).stream()
                .sorted(Comparator.comparing(MaterialBomItemEntity::getSort)
                        .thenComparing(MaterialBomItemEntity::getId)).toList();
        if (rows.isEmpty()) ParameterChecks.reject(ErrorCode.MATERIAL_BOM_INVALID);
        var result = new ArrayList<MaterialBomGraph.Node>();
        var seenMaterials = new HashSet<Long>();
        for (var line : rows) {
            if (++visited[0] > MAX_LINES) ParameterChecks.reject(ErrorCode.MATERIAL_BOM_BUDGET);
            if (!seenMaterials.add(line.getMaterialId())) conflict();
            decimal(line.getQuantity());
            var material = materialMap.get(line.getMaterialId());
            if (material == null || !Objects.equals(material.getUnitId(), line.getUnitId()) || Objects.equals(material.getId(), version.getProductId()))
                conflict();
            var unit = unitMap.get(line.getUnitId());
            if (unit == null || publishing && (!EnableStatus.ENABLED.getValue()
                    .equals(material.getStatus()) || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())))
                conflict();
            var quantity = output.multiply(line.getQuantity()).divide(version.getBaseQty(), MathContext.DECIMAL128);
            var childPath = path + "/" + line.getId();
            List<MaterialBomGraph.Node> children = List.of();
            if (line.getChildBomId() != null) {
                var child = versions.get(line.getChildBomId());
                if (child == null || !Objects.equals(child.getProductId(), material.getId())) conflict();
                children = walk(child.getId(), quantity, childPath, ancestors, versions, grouped, materialMap, unitMap, totals, publishing, depth + 1, visited);
            } else {
                var prior = totals.get(material.getId());
                BigDecimal sum = quantity;
                if (prior != null) sum = sum.add(prior.quantity());
                totals.put(material.getId(), new MaterialBomGraph.Total(material.getId(), material.getCode(), material.getName(), sum, unit.getName()));
            }
            result.add(new MaterialBomGraph.Node(childPath, material.getId(), material.getCode(), material.getName(), quantity.setScale(6, RoundingMode.HALF_UP), unit.getName(), line.getChildBomId(), children));
        }
        ancestors.remove(id);
        return result;
    }

    public void protectMaterials(List<Long> ids) {
        var roots = new MaterialBomExample();
        roots.createCriteria().andProductIdIn(ids);
        var lines = new MaterialBomItemExample();
        lines.createCriteria().andMaterialIdIn(ids);
        if (boms.countByExample(roots) > 0 || items.countByExample(lines) > 0) conflict();
    }

    public void protectUnits(List<Long> ids) {
        var roots = new MaterialBomExample();
        roots.createCriteria().andUnitIdIn(ids);
        var lines = new MaterialBomItemExample();
        lines.createCriteria().andUnitIdIn(ids);
        if (boms.countByExample(roots) > 0 || items.countByExample(lines) > 0) conflict();
    }

    private void lockMaterials(List<Long> ids) {
        if (ids.stream().anyMatch(Objects::isNull)) conflict();
        var rows = new ArrayList<MaterialEntity>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            var row = materials.selectByPrimaryKeyForUpdate(id);
            if (row == null || !EnableStatus.ENABLED.getValue().equals(row.getStatus()) || row.getUnitId() == null)
                conflict();
            rows.add(row);
        }
        for (Long id : rows.stream().map(MaterialEntity::getUnitId).distinct().sorted().toList()) {
            var unit = units.selectByPrimaryKeyForUpdate(id);
            if (unit == null || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())) conflict();
        }
    }

    private MaterialEntity lockMaterial(Long id) {
        lockMaterials(List.of(id));
        return materials.selectByPrimaryKey(id);
    }

    private MaterialBomEntity require(Long id) {
        var row = boms.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private MaterialBomEntity lock(Long id) {
        // The product lock serializes duplicate code/version creation and edits for a product.
        var original = require(id);
        lockMaterial(original.getProductId());
        var row = boms.selectByPrimaryKeyForUpdate(id);
        if (row == null) conflict();
        return row;
    }

    private MaterialBomEntity editable(Long id) {
        var row = lock(id);
        if (!MaterialBomStatus.DRAFT.getValue().equals(row.getStatus()))
            ParameterChecks.reject(ErrorCode.MATERIAL_BOM_FROZEN);
        return row;
    }

    private void unique(String code, String version, Long productId, Long excluding) {
        ParameterChecks.text(code, true, 64, ErrorCode.BAD_REQUEST);
        ParameterChecks.text(version, true, 32, ErrorCode.BAD_REQUEST);
        var f = new MaterialBomExample();
        var c = f.createCriteria().andCodeEqualTo(code).andVersionEqualTo(version).andProductIdEqualTo(productId);
        if (excluding != null) c.andIdNotEqualTo(excluding);
        if (boms.countByExample(f) > 0) conflict();
    }

    private void bump(MaterialBomEntity row) {
        row.setRevision(row.getRevision() + 1);
        changed(boms.updateByPrimaryKey(row));
    }

    private static void decimal(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 6 || value.precision() - value.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static void checkIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream()
                .anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static void changed(int n) {
        if (n != 1) conflict();
    }

    private static void conflict() {
        ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }
}
