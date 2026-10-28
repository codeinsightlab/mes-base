package dev.mesbase.business.sales.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.sales.dao.MesCustomerMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderItemMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderMapper;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemCreateCommand;
import dev.mesbase.business.sales.domain.MesSalesOrderItemEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemExample;
import dev.mesbase.business.sales.domain.MesSalesOrderItemUpdateCommand;
import dev.mesbase.business.sales.domain.MesSalesOrderResource;
import dev.mesbase.business.sales.domain.SalesOrderCreateRequest;
import dev.mesbase.business.sales.domain.SalesOrderDocument;
import dev.mesbase.business.sales.domain.SalesOrderLineRequest;
import dev.mesbase.business.sales.domain.SalesOrderUpdateRequest;
import dev.mesbase.business.sales.service.IMesSalesOrderItemService;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.business.sales.domain.SalesOrderStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.shared.enums.BusinessSource;

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
public class SalesOrderDocumentService {
    private final MesSalesOrderMapper orders;
    private final MesSalesOrderItemMapper items;
    private final MesCustomerMapper customers;
    private final MaterialMapper materials;
    private final IMesSalesOrderItemService itemService;
    private final MaterialQuantityRules quantityRules;

    public SalesOrderDocumentService(MesSalesOrderMapper orders, MesSalesOrderItemMapper items,
                                     MesCustomerMapper customers, MaterialMapper materials,
                                     IMesSalesOrderItemService itemService, MaterialQuantityRules quantityRules) {
        this.orders = orders;
        this.items = items;
        this.customers = customers;
        this.materials = materials;
        this.itemService = itemService;
        this.quantityRules = quantityRules;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public SalesOrderDocument get(long id) {
        MesSalesOrderEntity header = required(orders.selectByPrimaryKey(id));
        return new SalesOrderDocument(header, lines(id));
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesSalesOrderResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public SalesOrderDocument create(SalesOrderCreateRequest input) {
        requireLines(input.items());
        validateCustomer(input.customerId(), null);
        Map<Long, MaterialEntity> referencedMaterials = validateMaterials(input.items());
        long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        MesSalesOrderEntity header = new MesSalesOrderEntity();
        header.setOrderNo("SO-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        header.setCustomerId(input.customerId());
        header.setOrderDate(input.orderDate());
        header.setStatus(SalesOrderStatus.DRAFT.getValue());
        header.setSourceType(BusinessSource.LOCAL.getValue());
        header.setExternalId(null);
        header.setRemark(input.remark());
        header.setRevision(0);
        header.setCreatedBy(userId);
        header.setUpdatedBy(userId);
        header.setCreatedAt(now);
        header.setUpdatedAt(now);
        try {
            if (orders.insertSelective(header) != 1) conflict();
            saveNewLines(header.getId(), input.items(), referencedMaterials, userId, now);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        return get(header.getId());
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesSalesOrderResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public SalesOrderDocument update(long id, SalesOrderUpdateRequest input) {
        requireLines(input.items());
        MesSalesOrderEntity header = required(orders.selectByPrimaryKeyForUpdate(id));
        requireEditable(header, input.revision());
        validateCustomer(input.customerId(), Objects.equals(header.getCustomerId(), input.customerId())
                ? header.getCustomerId() : null);
        Map<Long, MaterialEntity> referencedMaterials = validateMaterials(input.items());
        List<MesSalesOrderItemEntity> currentLines = lines(id);
        Map<Long, MesSalesOrderItemEntity> existing = new HashMap<>();
        for (MesSalesOrderItemEntity line : currentLines) existing.put(line.getId(), line);
        Set<Long> retained = new HashSet<>();
        long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        header.setCustomerId(input.customerId());
        header.setOrderDate(input.orderDate());
        header.setRemark(input.remark());
        header.setRevision(header.getRevision() + 1);
        header.setUpdatedBy(userId);
        header.setUpdatedAt(now);
        try {
            if (orders.updateByPrimaryKeySelective(header) != 1) conflict();
            for (int index = 0; index < input.items().size(); index++) {
                SalesOrderLineRequest request = input.items().get(index);
                if (request.id() == null) {
                    insertLine(id, index + 1, request, referencedMaterials.get(request.materialId()), userId, now);
                } else {
                    MesSalesOrderItemEntity line = existing.get(request.id());
                    if (line == null || !retained.add(request.id())) conflict();
                    updateLine(id, index + 1, request, line, referencedMaterials.get(request.materialId()), userId, now);
                }
            }
            List<Long> removed = currentLines.stream().map(MesSalesOrderItemEntity::getId)
                    .filter(lineId -> !retained.contains(lineId)).toList();
            if (!removed.isEmpty()) itemService.deleteByIds(removed);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        return get(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesSalesOrderResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) conflict();
        for (Long id : ids.stream().sorted().toList()) {
            MesSalesOrderEntity header = required(orders.selectByPrimaryKeyForUpdate(id));
            requireEditable(header, header.getRevision());
            List<Long> childIds = lines(id).stream().map(MesSalesOrderItemEntity::getId).toList();
            if (!childIds.isEmpty()) itemService.deleteByIds(childIds);
            if (orders.deleteByPrimaryKey(id) != 1) conflict();
        }
    }

    private void saveNewLines(long orderId, List<SalesOrderLineRequest> requests,
                              Map<Long, MaterialEntity> referencedMaterials, long userId, LocalDateTime now) {
        for (int index = 0; index < requests.size(); index++) {
            SalesOrderLineRequest request = requests.get(index);
            if (request.id() != null) throw new FoundationException(ErrorCode.BAD_REQUEST);
            insertLine(orderId, index + 1, request, referencedMaterials.get(request.materialId()), userId, now);
        }
    }

    private void insertLine(long orderId, int lineNumber, SalesOrderLineRequest request,
                            MaterialEntity material, long userId, LocalDateTime now) {
        try {
            MesSalesOrderItemCreateCommand command = new MesSalesOrderItemCreateCommand(orderId, lineNumber,
                    request.materialId(), material.getUnitId(), request.orderedQty(), request.requiredDate(),
                    request.remark(), 0, userId, now, userId, now);
            itemService.insert(command);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private void updateLine(long orderId, int lineNumber, SalesOrderLineRequest request,
                            MesSalesOrderItemEntity existing, MaterialEntity material, long userId, LocalDateTime now) {
        try {
            MesSalesOrderItemUpdateCommand command = new MesSalesOrderItemUpdateCommand(orderId,
                    lineNumber, request.materialId(), material.getUnitId(), request.orderedQty(),
                    request.requiredDate(), request.remark(), existing.getRevision() + 1,
                    existing.getCreatedBy(), existing.getCreatedAt(), userId, now);
            itemService.updateById(existing.getId(), command);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private Map<Long, MaterialEntity> validateMaterials(List<SalesOrderLineRequest> requests) {
        List<Long> ids = requests.stream().map(SalesOrderLineRequest::materialId).distinct().sorted().toList();
        Map<Long, MaterialEntity> found = new HashMap<>();
        for (Long id : ids) {
            MaterialEntity material = materials.selectByPrimaryKeyForUpdate(id);
            if (material == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus()) || material.getUnitId() == null) {
                throw new FoundationException(ErrorCode.UNPROCESSABLE);
            }
            found.put(id, material);
        }
        quantityRules.lockForWrite(ids);
        for (SalesOrderLineRequest request : requests) {
            quantityRules.checkForWrite(request.orderedQty(), request.materialId());
        }
        return found;
    }

    private void validateCustomer(Long customerId, Long allowedDisabledId) {
        MesCustomerEntity customer = customers.selectByPrimaryKeyForUpdate(customerId);
        if (customer == null || !EnableStatus.ENABLED.getValue().equals(customer.getStatus())
                && !Objects.equals(customerId, allowedDisabledId)) {
            throw new FoundationException(ErrorCode.UNPROCESSABLE);
        }
    }

    private List<MesSalesOrderItemEntity> lines(long orderId) {
        MesSalesOrderItemExample example = new MesSalesOrderItemExample();
        example.createCriteria().andSalesOrderIdEqualTo(orderId);
        example.setOrderByClause("line_no asc");
        return items.selectByExample(example);
    }

    private static void requireEditable(MesSalesOrderEntity header, Integer revision) {
        if (!SalesOrderStatus.DRAFT.getValue().equals(header.getStatus()) || !BusinessSource.LOCAL.getValue().equals(header.getSourceType())
                || !Objects.equals(header.getRevision(), revision)) conflict();
    }

    private static void requireLines(List<SalesOrderLineRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 200 || values.stream().anyMatch(Objects::isNull)) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        for (SalesOrderLineRequest line : values) {
            BigDecimal quantity = line.orderedQty();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 || quantity.scale() > 6
                    || quantity.precision() - quantity.scale() > 14) {
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
