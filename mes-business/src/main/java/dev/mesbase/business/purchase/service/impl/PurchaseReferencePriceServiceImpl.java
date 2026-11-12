package dev.mesbase.business.purchase.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.purchase.dao.PurchaseReferencePriceMapper;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceCreateCommand;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceEntity;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceExample;
import dev.mesbase.business.purchase.domain.PurchaseReferencePricePage;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceQueryDto;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceResource;
import dev.mesbase.business.purchase.service.IPurchaseReferencePriceService;
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
 * 参考报价历史 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PurchaseReferencePriceServiceImpl implements IPurchaseReferencePriceService {
    private final dev.mesbase.business.purchase.dao.PurchaseReferencePriceQueryMapper queries;
    private final PurchaseReferencePriceMapper mapper;
    private final dev.mesbase.business.production.service.BusinessSourceRules sources;
    private final dev.mesbase.business.purchase.service.IPurchaseSupplierService suppliers;
    private final dev.mesbase.business.basic.dao.MaterialMapper materials;
    private final dev.mesbase.business.basic.dao.UnitMeasureMapper units;

    public PurchaseReferencePriceServiceImpl(PurchaseReferencePriceMapper mapper,
                                             dev.mesbase.business.production.service.BusinessSourceRules sources,
                                             dev.mesbase.business.purchase.service.IPurchaseSupplierService suppliers,
                                             dev.mesbase.business.basic.dao.MaterialMapper materials,
                                             dev.mesbase.business.basic.dao.UnitMeasureMapper units,
                                             dev.mesbase.business.purchase.dao.PurchaseReferencePriceQueryMapper queries) {
        this.queries = queries;
        this.units = units;
        this.sources = sources;
        this.suppliers = suppliers;
        this.materials = materials;
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public PurchaseReferencePricePage selectPage(PurchaseReferencePriceQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        PurchaseReferencePriceExample example = new PurchaseReferencePriceExample();
        PurchaseReferencePriceExample.Criteria criteria = example.createCriteria();
        if (query.supplierId() != null) {
            criteria.andSupplierIdEqualTo(query.supplierId());
        }
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo(query.unitId());
        }
        if (query.unitPrice() != null) {
            criteria.andUnitPriceEqualTo(query.unitPrice());
        }
        if (query.currency() != null) {
            criteria.andCurrencyLike("%" + query.currency() + "%");
        }
        if (query.effectiveDate() != null) {
            criteria.andEffectiveDateEqualTo(query.effectiveDate());
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id desc");
        try (Page<dev.mesbase.business.purchase.domain.PurchaseReferencePriceRow> page = PageHelper.offsetPage(offset, limit)) {
            var rows = queries.selectWithNames(example);
            return new PurchaseReferencePricePage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = PurchaseReferencePriceResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public PurchaseReferencePriceEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = PurchaseReferencePriceResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public PurchaseReferencePriceEntity insert(PurchaseReferencePriceCreateCommand input) {
        sources.localCreation();
        suppliers.lock(input.supplierId(), true);
        var material = materials.selectByPrimaryKeyForUpdate(input.materialId());
        if (material == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus())
                || !java.util.Objects.equals(material.getUnitId(), input.unitId()))
            PurchasePriceRules.reject("请选择启用的物料，并使用其基本单位报价");
        var unit = units.selectByPrimaryKeyForUpdate(input.unitId());
        if (unit == null || !EnableStatus.ENABLED.getValue().equals(unit.getStatus()))
            PurchasePriceRules.reject("报价基本单位必须启用");
        if (input.effectiveDate() == null) PurchasePriceRules.reject("报价生效日期不能为空");
        PurchasePriceRules.currency(input.currency());
        PurchasePriceRules.price(input.unitPrice(), input.remark());
        PurchaseReferencePriceEntity row = new PurchaseReferencePriceEntity();
        row.setCreatedBy(dev.mesbase.common.utils.SecurityUtils.getUserId());
        row.setCreatedAt(java.time.LocalDateTime.now());
        row.setSupplierId(input.supplierId());
        row.setMaterialId(input.materialId());
        row.setUnitId(input.unitId());
        row.setUnitPrice(input.unitPrice());
        row.setCurrency(input.currency());
        row.setEffectiveDate(input.effectiveDate());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public PurchaseReferencePriceEntity effective(Long supplierId, Long materialId, java.time.LocalDate date) {
        if (supplierId == null || materialId == null || date == null)
            PurchasePriceRules.reject("供应商、物料和业务日期不能为空");
        var example = new PurchaseReferencePriceExample();
        example.createCriteria().andSupplierIdEqualTo(supplierId).andMaterialIdEqualTo(materialId)
                .andEffectiveDateLessThanOrEqualTo(date);
        example.setOrderByClause("effective_date desc, id desc");
        try (Page<PurchaseReferencePriceEntity> page = PageHelper.offsetPage(0, 1, false)) {
            var rows = mapper.selectByExample(example);
            if (rows.isEmpty()) return null;
            return rows.get(0);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Preview preview(Long supplierId, Long materialId, java.time.LocalDate date,
                           java.math.BigDecimal quantity, java.math.BigDecimal unitPrice, String remark) {
        var reference = effective(supplierId, materialId, date);
        var actual = unitPrice;
        if (actual == null && reference != null) actual = reference.getUnitPrice();
        java.math.BigDecimal amount = null;
        if (actual != null && quantity != null)
            amount = PurchasePriceRules.amount(quantity, PurchasePriceRules.price(actual, remark));
        return new Preview(reference, amount);
    }

    private PurchaseReferencePriceEntity requireRecord(Long id) {
        PurchaseReferencePriceEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}
