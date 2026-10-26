package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.production.dao.MesProductionPlanItemMapper;
import dev.mesbase.business.production.dao.MesProductionPlanMapper;
import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemCreateCommand;
import dev.mesbase.business.production.domain.MesProductionPlanItemEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemExample;
import dev.mesbase.business.production.domain.MesProductionPlanItemUpdateCommand;
import dev.mesbase.business.production.domain.MesProductionPlanResource;
import dev.mesbase.business.production.domain.ProductionPlanCreateRequest;
import dev.mesbase.business.production.domain.ProductionPlanDocument;
import dev.mesbase.business.production.domain.ProductionPlanLineRequest;
import dev.mesbase.business.production.domain.ProductionPlanStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.production.domain.ProductionPlanUpdateRequest;
import dev.mesbase.business.production.service.IMesProductionPlanItemService;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionPlanDocumentService {
    private final MesProductionPlanMapper plans;
    private final MesProductionPlanItemMapper items;
    private final MaterialMapper materials;
    private final IMesProductionPlanItemService itemService;
    private final MaterialQuantityRules quantityRules;

    public ProductionPlanDocumentService(MesProductionPlanMapper plans, MesProductionPlanItemMapper items,
                                         MaterialMapper materials, IMesProductionPlanItemService itemService,
                                         MaterialQuantityRules quantityRules) {
        this.plans = plans;
        this.items = items;
        this.materials = materials;
        this.itemService = itemService;
        this.quantityRules = quantityRules;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ProductionPlanDocument get(long id) {
        MesProductionPlanEntity header = required(plans.selectByPrimaryKey(id));
        return new ProductionPlanDocument(header, lines(id));
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public ProductionPlanDocument create(ProductionPlanCreateRequest input) {
        requireLines(input.items());
        validateMaterials(input.items());
        long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        MesProductionPlanEntity header = new MesProductionPlanEntity();
        header.setPlanNo("PP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        header.setPlanDate(input.planDate());
        header.setStatus(ProductionPlanStatus.DRAFT.getValue());
        header.setRemark(input.remark());
        header.setRevision(0);
        header.setCreatedBy(userId);
        header.setUpdatedBy(userId);
        header.setCreatedAt(now);
        header.setUpdatedAt(now);
        try {
            if (plans.insertSelective(header) != 1) conflict();
            saveNewLines(header.getId(), input.items(), userId, now);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        return get(header.getId());
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public ProductionPlanDocument update(long id, ProductionPlanUpdateRequest input) {
        requireLines(input.items());
        MesProductionPlanEntity header = required(plans.selectByPrimaryKeyForUpdate(id));
        requireEditable(header, input.revision());
        validateMaterials(input.items());
        List<MesProductionPlanItemEntity> currentLines = lines(id);
        Map<Long, MesProductionPlanItemEntity> existing = new HashMap<>();
        for (MesProductionPlanItemEntity line : currentLines) existing.put(line.getId(), line);
        Set<Long> retained = new HashSet<>();
        long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        header.setPlanDate(input.planDate());
        header.setRemark(input.remark());
        header.setRevision(header.getRevision() + 1);
        header.setUpdatedBy(userId);
        header.setUpdatedAt(now);
        try {
            if (plans.updateByPrimaryKeySelective(header) != 1) conflict();
            for (int index = 0; index < input.items().size(); index++) {
                ProductionPlanLineRequest request = input.items().get(index);
                if (request.id() == null) {
                    insertLine(id, index + 1, request, userId, now);
                } else {
                    MesProductionPlanItemEntity line = existing.get(request.id());
                    if (line == null || !retained.add(request.id())) conflict();
                    updateLine(id, index + 1, request, line, userId, now);
                }
            }
            List<Long> removed = currentLines.stream().map(MesProductionPlanItemEntity::getId)
                    .filter(lineId -> !retained.contains(lineId)).toList();
            if (!removed.isEmpty()) itemService.deleteByIds(removed);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        return get(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionPlanResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) conflict();
        for (Long id : ids.stream().sorted().toList()) {
            MesProductionPlanEntity header = required(plans.selectByPrimaryKeyForUpdate(id));
            requireEditable(header, header.getRevision());
            List<Long> childIds = lines(id).stream().map(MesProductionPlanItemEntity::getId).toList();
            if (!childIds.isEmpty()) itemService.deleteByIds(childIds);
            if (plans.deleteByPrimaryKey(id) != 1) conflict();
        }
    }

    private void saveNewLines(long planId, List<ProductionPlanLineRequest> requests, long userId, LocalDateTime now) {
        for (int index = 0; index < requests.size(); index++) {
            ProductionPlanLineRequest request = requests.get(index);
            if (request.id() != null) throw new FoundationException(ErrorCode.BAD_REQUEST);
            insertLine(planId, index + 1, request, userId, now);
        }
    }

    private void insertLine(long planId, int lineNumber, ProductionPlanLineRequest request,
                            long userId, LocalDateTime now) {
        try {
            MesProductionPlanItemCreateCommand command = new MesProductionPlanItemCreateCommand(planId, lineNumber,
                    request.materialId(), request.plannedQty(), request.plannedStartAt(), request.plannedEndAt(),
                    priority(request.priority()), request.remark(), 0, userId, now, userId, now);
            itemService.insert(command);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private void updateLine(long planId, int lineNumber, ProductionPlanLineRequest request,
                            MesProductionPlanItemEntity existing, long userId, LocalDateTime now) {
        try {
            MesProductionPlanItemUpdateCommand command = new MesProductionPlanItemUpdateCommand(planId, lineNumber,
                    request.materialId(), request.plannedQty(), request.plannedStartAt(), request.plannedEndAt(),
                    priority(request.priority()), request.remark(), existing.getRevision() + 1,
                    existing.getCreatedBy(), existing.getCreatedAt(), userId, now);
            itemService.updateById(existing.getId(), command);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private void validateMaterials(List<ProductionPlanLineRequest> requests) {
        List<Long> ids = requests.stream().map(ProductionPlanLineRequest::materialId).distinct().sorted().toList();
        for (Long id : ids) {
            MaterialEntity material = materials.selectByPrimaryKeyForUpdate(id);
            if (material == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus())) {
                throw new FoundationException(ErrorCode.UNPROCESSABLE);
            }
        }
        for (ProductionPlanLineRequest request : requests)
            quantityRules.checkForWrite(request.plannedQty(), request.materialId());
    }

    private List<MesProductionPlanItemEntity> lines(long planId) {
        MesProductionPlanItemExample example = new MesProductionPlanItemExample();
        example.createCriteria().andPlanIdEqualTo(planId);
        example.setOrderByClause("line_no asc");
        return items.selectByExample(example);
    }

    private static int priority(Integer priority) {
        return priority == null ? 0 : priority;
    }

    private static void requireEditable(MesProductionPlanEntity header, Integer revision) {
        if (!ProductionPlanStatus.DRAFT.getValue().equals(header.getStatus())
                || !Objects.equals(header.getRevision(), revision)) conflict();
    }

    private static void requireLines(List<ProductionPlanLineRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 200 || values.stream().anyMatch(Objects::isNull)) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        for (ProductionPlanLineRequest line : values) {
            BigDecimal quantity = line.plannedQty();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 || quantity.scale() > 6
                    || quantity.precision() - quantity.scale() > 14) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
            if (line.plannedStartAt() != null && line.plannedEndAt() != null
                    && line.plannedEndAt().isBefore(line.plannedStartAt())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
    }

    private static long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser user) || user.getUserId() == null) {
            throw new FoundationException(ErrorCode.UNAUTHENTICATED);
        }
        return user.getUserId();
    }

    private static <T> T required(T value) {
        if (value == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return value;
    }

    private static void conflict() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
