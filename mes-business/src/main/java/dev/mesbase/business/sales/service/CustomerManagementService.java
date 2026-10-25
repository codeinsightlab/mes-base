package dev.mesbase.business.sales.service;

import dev.mesbase.business.sales.dao.MesCustomerMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderMapper;
import dev.mesbase.business.sales.domain.CustomerCreateRequest;
import dev.mesbase.business.sales.domain.CustomerUpdateRequest;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesCustomerExample;
import dev.mesbase.business.sales.domain.MesCustomerResource;
import dev.mesbase.business.sales.domain.MesSalesOrderExample;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class CustomerManagementService {
    private final MesCustomerMapper customers;
    private final MesSalesOrderMapper orders;

    public CustomerManagementService(MesCustomerMapper customers, MesSalesOrderMapper orders) {
        this.customers = customers;
        this.orders = orders;
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesCustomerResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST)
    public MesCustomerEntity create(CustomerCreateRequest input) {
        validateText(input.code(), true, 64);
        validateText(input.name(), true, 255);
        validateText(input.shortName(), false, 128);
        validateText(input.contactPerson(), false, 128);
        validateText(input.contactPhone(), false, 64);
        validateText(input.address(), false, 500);
        validateText(input.remark(), false, 500);
        long userId = currentUserId();
        MesCustomerEntity row = new MesCustomerEntity();
        row.setCode(input.code());
        row.setName(input.name());
        row.setShortName(input.shortName());
        row.setContactPerson(input.contactPerson());
        row.setContactPhone(input.contactPhone());
        row.setAddress(input.address());
        row.setStatus("ENABLED");
        row.setSourceType("LOCAL");
        row.setRevision(0);
        row.setRemark(input.remark());
        row.setCreatedBy(userId);
        row.setUpdatedBy(userId);
        try {
            if (customers.insertSelective(row) != 1) conflict();
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        return required(customers.selectByPrimaryKey(row.getId()));
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesCustomerResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesCustomerEntity update(long id, CustomerUpdateRequest input) {
        MesCustomerEntity row = required(customers.selectByPrimaryKeyForUpdate(id));
        if (!Objects.equals(row.getRevision(), input.revision()) || !"LOCAL".equals(row.getSourceType())) conflict();
        validateText(input.code(), true, 64);
        validateText(input.name(), true, 255);
        validateText(input.shortName(), false, 128);
        validateText(input.contactPerson(), false, 128);
        validateText(input.contactPhone(), false, 64);
        validateText(input.address(), false, 500);
        validateText(input.remark(), false, 500);
        long userId = currentUserId();
        row.setCode(input.code());
        row.setName(input.name());
        row.setShortName(input.shortName());
        row.setContactPerson(input.contactPerson());
        row.setContactPhone(input.contactPhone());
        row.setAddress(input.address());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        row.setRevision(row.getRevision() + 1);
        row.setUpdatedBy(userId);
        try {
            if (customers.updateByPrimaryKeySelective(row) != 1) conflict();
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        return required(customers.selectByPrimaryKey(id));
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesCustomerResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) conflict();
        for (Long id : ids.stream().sorted().toList()) {
            MesCustomerEntity row = required(customers.selectByPrimaryKeyForUpdate(id));
            if (!"LOCAL".equals(row.getSourceType())) conflict();
            MesSalesOrderExample references = new MesSalesOrderExample();
            references.createCriteria().andCustomerIdEqualTo(id);
            if (orders.countByExample(references) > 0) conflict();
            if (customers.deleteByPrimaryKey(id) != 1) conflict();
        }
    }

    private static void validateText(String value, boolean required, int maximum) {
        if (required && StringUtils.isBlank(value) || value != null && value.length() > maximum) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
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
