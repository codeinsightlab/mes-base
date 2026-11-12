package dev.mesbase.business.purchase.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.purchase.dao.PurchaseSupplierMapper;
import dev.mesbase.business.purchase.domain.PurchaseSupplierCreateCommand;
import dev.mesbase.business.purchase.domain.PurchaseSupplierEntity;
import dev.mesbase.business.purchase.domain.PurchaseSupplierExample;
import dev.mesbase.business.purchase.domain.PurchaseSupplierPage;
import dev.mesbase.business.purchase.domain.PurchaseSupplierQueryDto;
import dev.mesbase.business.purchase.domain.PurchaseSupplierResource;
import dev.mesbase.business.purchase.domain.PurchaseSupplierUpdateCommand;
import dev.mesbase.business.purchase.service.IPurchaseSupplierService;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PurchaseSupplierServiceImpl implements IPurchaseSupplierService {
    private final PurchaseSupplierMapper mapper;
    private final dev.mesbase.business.production.service.BusinessSourceRules sources;
    private final dev.mesbase.business.purchase.dao.PurchaseReferencePriceMapper prices;
    private final dev.mesbase.business.wm.dao.WmStockDocumentMapper documents;

    public PurchaseSupplierServiceImpl(PurchaseSupplierMapper mapper,
                                       dev.mesbase.business.production.service.BusinessSourceRules sources,
                                       dev.mesbase.business.purchase.dao.PurchaseReferencePriceMapper prices,
                                       dev.mesbase.business.wm.dao.WmStockDocumentMapper documents) {
        this.sources = sources;
        this.prices = prices;
        this.documents = documents;
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = PurchaseSupplierResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public PurchaseSupplierPage selectPage(PurchaseSupplierQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        PurchaseSupplierExample example = new PurchaseSupplierExample();
        PurchaseSupplierExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.contactPerson() != null) {
            criteria.andContactPersonLike("%" + query.contactPerson() + "%");
        }
        if (query.contactPhone() != null) {
            criteria.andContactPhoneLike("%" + query.contactPhone() + "%");
        }
        if (query.address() != null) {
            criteria.andAddressLike("%" + query.address() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<PurchaseSupplierEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<PurchaseSupplierEntity> rows = mapper.selectByExample(example);
            return new PurchaseSupplierPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = PurchaseSupplierResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public PurchaseSupplierEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = PurchaseSupplierResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public PurchaseSupplierEntity insert(PurchaseSupplierCreateCommand input) {
        sources.localCreation();
        validate(input.code(), input.name(), input.status());
        PurchaseSupplierEntity row = new PurchaseSupplierEntity();
        row.setCreatedBy(dev.mesbase.common.utils.SecurityUtils.getUserId());
        row.setCreatedAt(java.time.LocalDateTime.now());
        audit(row);
        row.setCode(input.code().trim());
        row.setName(input.name().trim());
        row.setContactPerson(input.contactPerson());
        row.setContactPhone(input.contactPhone());
        row.setAddress(input.address());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = PurchaseSupplierResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public PurchaseSupplierEntity updateById(Long id, PurchaseSupplierUpdateCommand input) {
        sources.localCreation();
        validate(input.code(), input.name(), input.status());
        PurchaseSupplierEntity row = lock(id, false);
        audit(row);
        row.setId(id);
        row.setCode(input.code().trim());
        row.setName(input.name().trim());
        row.setContactPerson(input.contactPerson());
        row.setContactPhone(input.contactPhone());
        row.setAddress(input.address());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = PurchaseSupplierResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        sources.localCreation();
        PurchaseSupplierExample example = new PurchaseSupplierExample();
        // Bounded at 100, sorted locks prevent multi-delete deadlocks; any failure rolls back all.
        for (var id : ids.stream().sorted().toList()) {
            lock(id, false);
            var q = new dev.mesbase.business.purchase.domain.PurchaseReferencePriceExample();
            q.createCriteria().andSupplierIdEqualTo(id);
            var d = new dev.mesbase.business.wm.domain.WmStockDocumentExample();
            d.createCriteria().andSupplierIdEqualTo(id);
            if (prices.countByExample(q) > 0 || documents.countByExample(d) > 0)
                PurchasePriceRules.reject("供应商已被采购单据或报价引用，请停用");
        }
        example.createCriteria().andIdIn(ids)
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public PurchaseSupplierEntity lock(Long id, boolean enabled) {
        if (id == null) PurchasePriceRules.reject("供应商不能为空");
        var row = mapper.selectByPrimaryKeyForUpdate(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (enabled && !EnableStatus.ENABLED.getValue().equals(row.getStatus()))
            PurchasePriceRules.reject("请选择启用的供应商");
        return row;
    }

    private static void validate(String code, String name, String status) {
        if (code == null || code.isBlank() || code.length() > 64 || name == null || name.isBlank() || name.length() > 200
                || !(EnableStatus.ENABLED.getValue().equals(status) || EnableStatus.DISABLED.getValue().equals(status)))
            PurchasePriceRules.reject("供应商编码、名称或状态无效");
    }

    private static void audit(PurchaseSupplierEntity row) {
        row.setUpdatedBy(dev.mesbase.common.utils.SecurityUtils.getUserId());
        row.setUpdatedAt(java.time.LocalDateTime.now());
    }

    private int insertUnique(PurchaseSupplierEntity row) {
        try {
            return mapper.insert(row);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT,
                    new dev.mesbase.core.error.SafeBusinessMessage("供应商编码已存在"));
        }
    }

    private int updateUnique(PurchaseSupplierEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT,
                    new dev.mesbase.core.error.SafeBusinessMessage("供应商编码已存在"));
        }
    }

    private PurchaseSupplierEntity requireRecord(Long id) {
        PurchaseSupplierEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}
