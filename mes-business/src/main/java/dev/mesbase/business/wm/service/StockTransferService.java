package dev.mesbase.business.wm.service;

import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmInventoryCreateCommand;
import dev.mesbase.business.shared.enums.EnableStatus;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Synchronous, same-factory LOCAL transfer. Both documents and every stock action share one transaction.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class StockTransferService {
    public record Item(@NotNull Long sourceStockId, Long targetStockId, Long targetWarehouseId,
                       @Size(max = 64) String targetBinCode,
                       @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                       @Size(max = 500) String remark) {
    }

    public record Save(@NotBlank String requestId, Long expectedRevision,
                       @NotBlank @Size(max = 500) String reason, @Size(max = 500) String remark,
                       @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items) {
    }

    private record CreateFingerprint(String requestId, String requestHash) {
    }

    public record Query(String status, String documentNo, Integer offset, Integer limit) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public StockDocumentModels.Page page(Query query) {
        return documents.page(new StockDocumentModels.Query(StockDocumentDirection.OUT.getValue(), StockDocumentType.TRANSFER_ISSUE.getValue(),
                query.status(), query.documentNo(), query.offset(), query.limit()));
    }

    public record Detail(StockDocumentModels.Detail outbound, StockDocumentModels.Detail inbound) {
    }

    private final StockDocumentService documents;
    private final WmStockDocumentMapper headers;
    private final WmStockDocumentLineMapper lines;
    private final StockDocumentExtraMapper links;
    private final WmInventoryMapper inventories;
    private final IWmInventoryService inventoryService;
    private final StockReferenceRules stockReferences;
    private final MaterialMapper materials;
    private final LocalStockService stock;
    private final MaterialQuantityRules quantities;
    private final BusinessSourceRules sources;
    private final AutoCodeNumbering numbering;
    private final Validator validator;
    private final TransactionTemplate transactions;

    public StockTransferService(StockDocumentService documents, WmStockDocumentMapper headers,
                                WmStockDocumentLineMapper lines, StockDocumentExtraMapper links,
                                WmInventoryMapper inventories, IWmInventoryService inventoryService,
                                StockReferenceRules stockReferences,
                                MaterialMapper materials, LocalStockService stock, MaterialQuantityRules quantities,
                                BusinessSourceRules sources, AutoCodeNumbering numbering, Validator validator,
                                @Qualifier("businessTransactionManager") PlatformTransactionManager manager) {
        this.documents = documents;
        this.headers = headers;
        this.lines = lines;
        this.links = links;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.stockReferences = stockReferences;
        this.materials = materials;
        this.stock = stock;
        this.quantities = quantities;
        this.sources = sources;
        this.numbering = numbering;
        this.validator = validator;
        transactions = new TransactionTemplate(manager);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Detail create(Save command) {
        validate(command);
        uuid(command.requestId());
        if (command.expectedRevision() != null) reject();
        String frozen = new ObjectMapper().writeValueAsString(new CreateFingerprint(command.requestId(),
                RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(command))));
        var existing = transactions.execute(status -> find(command.requestId(), frozen));
        if (existing != null) return existing;
        String outNo = numbering.getBusinessDocNo(StockDocumentType.TRANSFER_ISSUE.getValue(), AutoCodeRuleType.STOCK_OUT.name());
        String inNo = numbering.getBusinessDocNo(StockDocumentType.TRANSFER_RECEIPT.getValue(), AutoCodeRuleType.STOCK_IN.name());
        try {
            return transactions.execute(status -> {
                var replay = find(command.requestId(), frozen);
                if (replay != null) return replay;
                var out = documents.createWithNumber(new StockDocumentModels.Create(StockDocumentDirection.OUT.getValue(), StockDocumentType.TRANSFER_ISSUE.getValue(), command.reason(), command.remark(), null, null, StockDocumentType.TRANSFER_ISSUE.getValue()), outNo);
                var in = documents.createWithNumber(new StockDocumentModels.Create(StockDocumentDirection.IN.getValue(), StockDocumentType.TRANSFER_RECEIPT.getValue(), command.reason(), command.remark(), null, null, StockDocumentType.TRANSFER_RECEIPT.getValue()), inNo);
                Long id = out.document().getId(), paired = in.document().getId();
                if (headers.pairDocuments(id, paired) != 1 || headers.pairDocuments(paired, id) != 1) reject();
                replace(id, paired, command);
                var row = headers.selectByPrimaryKey(id);
                row.setCreateRequestId(command.requestId());
                row.setSourceRequestJson(frozen);
                if (headers.updateByPrimaryKeyWithBLOBs(row) != 1) reject();
                return detail(id);
            });
        } catch (DuplicateKeyException conflict) {
            var replay = transactions.execute(status -> find(command.requestId(), frozen));
            if (replay != null) return replay;
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, conflict);
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(Long id) {
        var out = documents.detail(id);
        Long paired = links.pairedDocumentId(id);
        if (!StockDocumentType.TRANSFER_ISSUE.getValue().equals(out.document().getDocumentType()) || paired == null)
            throw new FoundationException(ErrorCode.NOT_FOUND);
        var in = documents.detail(paired);
        if (!StockDocumentType.TRANSFER_RECEIPT.getValue()
                .equals(in.document().getDocumentType()) || !id.equals(links.pairedDocumentId(paired))) reject();
        return new Detail(out, in);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detailByDocumentId(Long documentId) {
        var document = documents.detail(documentId).document();
        if (StockDocumentType.TRANSFER_RECEIPT.getValue().equals(document.getDocumentType())) {
            Long outboundId = links.pairedDocumentId(documentId);
            if (outboundId == null) throw new FoundationException(ErrorCode.NOT_FOUND);
            return detail(outboundId);
        }
        return detail(documentId);
    }

    public Detail update(Long id, Save command) {
        validate(command);
        uuid(command.requestId());
        var pair = lockPair(id);
        if (!Objects.equals(pair.outbound().document().getCreateRequestId(), command.requestId())) reject();
        draft(pair.outbound().document(), command.expectedRevision());
        replace(id, pair.inbound().document().getId(), command);
        return detail(id);
    }

    public Detail cancel(Long id, StockDocumentModels.Version command) {
        validate(command);
        var pair = lockPair(id);
        draft(pair.outbound().document(), command.expectedRevision());
        documents.cancel(id, command);
        documents.cancel(pair.inbound().document().getId(), new StockDocumentModels.Version(pair.inbound().document()
                .getRevision()));
        return detail(id);
    }

    public Detail approve(Long id, StockDocumentModels.Post command) {
        validate(command);
        uuid(command.requestId());
        var pair = lockPair(id);
        String hash = RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(List.of(id, command.requestId(), command.expectedRevision(), SecurityUtils.getUserId())));
        var out = pair.outbound().document();
        var in = pair.inbound().document();
        if (StockDocumentStatus.POSTED.getValue().equals(out.getStatus())) {
            if (!command.requestId().equals(out.getPostRequestId()) || !hash.equals(out.getPostRequestHash())
                    || !StockDocumentStatus.POSTED.getValue()
                    .equals(in.getStatus()) || !hash.equals(in.getPostRequestHash())) reject();
            return pair;
        }
        draft(out, command.expectedRevision());
        draft(in, in.getRevision());
        sources.localCreation();
        var outgoing = pair.outbound().lines();
        var incoming = pair.inbound().lines();
        if (outgoing.isEmpty() || outgoing.size() != incoming.size()) reject();
        var all = new ArrayList<WmStockDocumentLineEntity>();
        all.addAll(outgoing);
        all.addAll(incoming);
        quantities.lockForWrite(all.stream().map(WmStockDocumentLineEntity::getMaterialId).distinct().sorted()
                .toList());
        stockReferences.lockInventoryReferences(all.stream().map(WmStockDocumentLineEntity::getMaterialId).distinct()
                        .sorted().toList(),
                all.stream().map(WmStockDocumentLineEntity::getWarehouseId).distinct().sorted().toList());
        all.stream().map(WmStockDocumentLineEntity::getStockId).distinct().sorted().forEach(stock::lock);
        for (int i = 0; i < outgoing.size(); i++) {
            var a = outgoing.get(i);
            var b = incoming.get(i);
            identity(a.getStockId(), b.getStockId());
            if (!Objects.equals(a.getLineNo(), b.getLineNo()) || !a.getMaterialId().equals(b.getMaterialId())
                    || !a.getUnitId().equals(b.getUnitId()) || !Objects.equals(a.getBatchNo(), b.getBatchNo())
                    || a.getQuantity().compareTo(b.getQuantity()) != 0) reject();
            quantities.checkSettlementForWrite(a.getQuantity(), null, a.getMaterialId());
        }
        // Stock rows are already locked in ID order; applying all issues first prevents self-funding cycles.
        for (var line : outgoing) post(out, line, command.requestId(), hash, line == outgoing.getFirst());
        String inboundRequest = UUID.nameUUIDFromBytes((command.requestId() + ":transfer-inbound").getBytes(StandardCharsets.UTF_8))
                .toString();
        for (var line : incoming) post(in, line, inboundRequest, hash, false);
        for (var row : List.of(out, in)) {
            row.setStatus(StockDocumentStatus.POSTED.getValue());
            row.setPostRequestId(row.getId().equals(id) ? command.requestId() : inboundRequest);
            row.setPostRequestHash(hash);
            row.setPostedBy(SecurityUtils.getUserId());
            row.setPostedAt(LocalDateTime.now());
            row.setUpdateBy(SecurityUtils.getUserId().toString());
            row.setUpdateTime(row.getPostedAt());
            row.setRevision(row.getRevision() + 1);
            try {
                if (headers.updateByPrimaryKeyWithBLOBs(row) != 1) reject();
            } catch (DuplicateKeyException conflict) {
                throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, conflict);
            }
        }
        return detail(id);
    }

    private void post(WmStockDocumentEntity row, WmStockDocumentLineEntity line, String operation, String hash,
                      boolean first) {
        String action = operation;
        if (!first) action = UUID.nameUUIDFromBytes((operation + ":" + line.getId()).getBytes(StandardCharsets.UTF_8))
                .toString();
        try {
            line.setStockActionId(stock.postDocument(row.getDirection(), row.getDocumentType(), row.getId(), line, operation, action, hash, row.getReason(), null)
                    .getId());
            if (lines.updateByPrimaryKey(line) != 1) reject();
        } catch (DuplicateKeyException conflict) {
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, conflict);
        }
    }

    private Detail lockPair(Long id) {
        var initial = detail(id);
        if (!StockDocumentDirection.OUT.getValue().equals(initial.outbound().document().getDirection())
                || !StockDocumentDirection.IN.getValue().equals(initial.inbound().document().getDirection())) reject();
        for (Long key : List.of(id, initial.inbound().document().getId()).stream().sorted().toList()) {
            if (headers.selectByPrimaryKeyForUpdate(key) == null) reject();
        }
        return detail(id);
    }

    private void replace(Long outId, Long inId, Save command) {
        var ids = new HashSet<Long>();
        for (var item : command.items()) {
            ids.add(item.sourceStockId());
            if (item.targetStockId() != null) ids.add(item.targetStockId());
            else if (item.targetWarehouseId() == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var query = new WmInventoryExample();
        query.createCriteria().andIdIn(ids.stream().sorted().toList());
        Map<Long, WmInventoryEntity> stocks = inventories.selectByExample(query).stream()
                .collect(Collectors.toMap(WmInventoryEntity::getId, row -> row));
        if (stocks.size() != ids.size()) reject();
        var materialIds = command.items().stream().map(item -> stocks.get(item.sourceStockId()).getMaterialId())
                .distinct().sorted().toList();
        var warehouseIds = new HashSet<Long>();
        for (var item : command.items()) {
            warehouseIds.add(stocks.get(item.sourceStockId()).getWarehouseId());
            if (item.targetStockId() != null) warehouseIds.add(stocks.get(item.targetStockId()).getWarehouseId());
            if (item.targetWarehouseId() != null) warehouseIds.add(item.targetWarehouseId());
        }
        quantities.lockForWrite(materialIds);
        stockReferences.lockInventoryReferences(materialIds, warehouseIds.stream().sorted().toList());
        var outgoing = new ArrayList<StockDocumentModels.AggregateLine>();
        var incoming = new ArrayList<StockDocumentModels.AggregateLine>();
        var existingOut = documents.detail(outId).lines();
        var existingIn = documents.detail(inId).lines();
        int index = 0;
        var sourceIds = new HashSet<Long>();
        var targetIds = new HashSet<Long>();
        for (var item : command.items()) {
            Long targetId = resolveTarget(stocks.get(item.sourceStockId()), item, stocks);
            identity(item.sourceStockId(), targetId);
            sourceIds.add(item.sourceStockId());
            targetIds.add(targetId);
            Long sourceLineId = null;
            Long targetLineId = null;
            if (index < existingOut.size()) sourceLineId = existingOut.get(index).getId();
            if (index < existingIn.size()) targetLineId = existingIn.get(index).getId();
            outgoing.add(line(item.sourceStockId(), item, sourceLineId));
            incoming.add(line(targetId, item, targetLineId));
            index++;
        }
        if (sourceIds.stream().anyMatch(targetIds::contains)) reject();
        var stockIds = new HashSet<Long>(sourceIds);
        stockIds.addAll(targetIds);
        stockIds.stream().sorted().forEach(stock::lock);
        documents.updateAggregate(outId, aggregate(StockDocumentDirection.OUT.getValue(), StockDocumentType.TRANSFER_ISSUE.getValue(), outId, command, outgoing));
        documents.updateAggregate(inId, aggregate(StockDocumentDirection.IN.getValue(), StockDocumentType.TRANSFER_RECEIPT.getValue(), inId, command, incoming));
    }

    private Long resolveTarget(WmInventoryEntity source, Item item, Map<Long, WmInventoryEntity> stocks) {
        if (item.targetStockId() != null) {
            var target = stocks.get(item.targetStockId());
            if (item.targetWarehouseId() != null && !item.targetWarehouseId().equals(target.getWarehouseId())
                    || item.targetBinCode() != null && !Objects.equals(item.targetBinCode(), target.getBinCode()))
                reject();
            return target.getId();
        }
        sources.localCreation();
        sources.warehouse(BusinessSource.LOCAL.getValue(), item.targetWarehouseId());
        var query = new WmInventoryExample();
        var identity = query.createCriteria().andMaterialIdEqualTo(source.getMaterialId())
                .andWarehouseIdEqualTo(item.targetWarehouseId());
        if (source.getBatchNo() == null) identity.andBatchNoIsNull();
        else identity.andBatchNoEqualTo(source.getBatchNo());
        if (item.targetBinCode() == null) identity.andBinCodeIsNull();
        else identity.andBinCodeEqualTo(item.targetBinCode());
        // Material/reference locks serialize both this resolver and the existing formal inventory creation service.
        List<WmInventoryEntity> matches;
        try (var page = PageHelper.offsetPage(0, 2, false)) {
            matches = inventories.selectByExample(query);
        }
        if (matches.size() > 1) reject();
        if (!matches.isEmpty()) {
            var target = matches.getFirst();
            if (!Objects.equals(source.getBatchNo(), target.getBatchNo()) || !Objects.equals(item.targetBinCode(), target.getBinCode()))
                reject();
            return target.getId();
        }
        return inventoryService.insert(new WmInventoryCreateCommand(source.getMaterialId(), item.targetWarehouseId(),
                        BigDecimal.ZERO, BigDecimal.ZERO, EnableStatus.ENABLED.getValue(), null, source.getBatchNo(), item.targetBinCode()))
                .inventory().getId();
    }

    private StockDocumentModels.AggregateSave aggregate(String direction, String type, Long id, Save c,
                                                        List<StockDocumentModels.AggregateLine> items) {
        return new StockDocumentModels.AggregateSave(new StockDocumentModels.AggregateHeader(direction, type, c.reason(), c.remark(), null, null, type, headers.selectByPrimaryKey(id)
                .getRevision()), items);
    }

    private StockDocumentModels.AggregateLine line(Long id, Item item, Long lineId) {
        var s = inventories.selectByPrimaryKey(id);
        var material = materials.selectByPrimaryKey(s.getMaterialId());
        if (material == null) reject();
        return new StockDocumentModels.AggregateLine(lineId, id, s.getMaterialId(), s.getWarehouseId(), material.getUnitId(), s.getBatchNo(), s.getBinCode(), item.quantity(), item.remark());
    }

    private void identity(Long source, Long target) {
        if (source.equals(target)) reject();
        var a = inventories.selectByPrimaryKey(source);
        var b = inventories.selectByPrimaryKey(target);
        if (a == null || b == null || !a.getMaterialId()
                .equals(b.getMaterialId()) || !Objects.equals(a.getBatchNo(), b.getBatchNo())
                || Objects.equals(a.getWarehouseId(), b.getWarehouseId()) && Objects.equals(a.getBinCode(), b.getBinCode()))
            reject();
        sources.localCreation();
        sources.warehouse(BusinessSource.LOCAL.getValue(), a.getWarehouseId());
        sources.warehouse(BusinessSource.LOCAL.getValue(), b.getWarehouseId());
    }

    private Detail find(String request, String frozen) {
        var query = new WmStockDocumentExample();
        query.createCriteria().andCreateRequestIdEqualTo(request);
        var found = headers.selectByExampleWithBLOBs(query);
        if (found.isEmpty()) return null;
        var row = found.getFirst();
        if (!StockDocumentType.TRANSFER_ISSUE.getValue()
                .equals(row.getDocumentType()) || !frozen.equals(row.getSourceRequestJson())) reject();
        return detail(row.getId());
    }

    private void validate(Object value) {
        if (!validator.validate(value).isEmpty()) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static void uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) reject();
        } catch (IllegalArgumentException e) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
    }

    private static void draft(WmStockDocumentEntity row, Long revision) {
        if (!StockDocumentStatus.DRAFT.getValue()
                .equals(row.getStatus()) || !Objects.equals(row.getRevision(), revision)) reject();
    }

    private static void reject() {
        ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
    }
}
