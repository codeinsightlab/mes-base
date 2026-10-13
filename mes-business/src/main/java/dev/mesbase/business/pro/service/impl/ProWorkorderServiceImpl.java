// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.pro.dao.ProWorkorderMapper;
import dev.mesbase.business.pro.domain.ProWorkorderCreateCommand;
import dev.mesbase.business.pro.domain.ProWorkorderEntity;
import dev.mesbase.business.pro.domain.ProWorkorderExample;
import dev.mesbase.business.pro.domain.ProWorkorderPage;
import dev.mesbase.business.pro.domain.ProWorkorderQueryDto;
import dev.mesbase.business.pro.domain.ProWorkorderResource;
import dev.mesbase.business.pro.domain.ProWorkorderUpdateCommand;
import dev.mesbase.business.pro.service.IProWorkorderService;
import dev.mesbase.business.pro.sync.ExternalProWorkorder;
import dev.mesbase.business.pro.sync.ProWorkorderQueryPort;
import dev.mesbase.business.pro.sync.ProWorkorderSyncResult;
import dev.mesbase.business.pro.sync.UnconfiguredProWorkorderQueryAdapter;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产工单 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProWorkorderServiceImpl implements IProWorkorderService {
    private final ProWorkorderMapper mapper;
    private final ProWorkorderQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IProWorkorderService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public ProWorkorderServiceImpl(ProWorkorderMapper mapper,
            ObjectProvider<ProWorkorderQueryPort> queries,
            SyncCoordinator coordinator,
            ObjectProvider<IProWorkorderService> self,
            Validator validator, BusinessCacheProcessor caches) {
        this.mapper = mapper;
        this.query = queries.getIfAvailable(UnconfiguredProWorkorderQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = ProWorkorderResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public ProWorkorderPage selectPage(ProWorkorderQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        ProWorkorderExample example = new ProWorkorderExample();
        ProWorkorderExample.Criteria criteria = example.createCriteria();
        if (query.workorderCode() != null) {
            criteria.andWorkorderCodeLike("%" + query.workorderCode() + "%");
        }
        if (query.workorderName() != null) {
            criteria.andWorkorderNameLike("%" + query.workorderName() + "%");
        }
        if (query.orderSource() != null) {
            criteria.andOrderSourceLike("%" + query.orderSource() + "%");
        }
        if (query.sourceCode() != null) {
            criteria.andSourceCodeLike("%" + query.sourceCode() + "%");
        }
        if (query.productId() != null) {
            criteria.andProductIdEqualTo( query.productId());
        }
        if (query.productCode() != null) {
            criteria.andProductCodeLike("%" + query.productCode() + "%");
        }
        if (query.productName() != null) {
            criteria.andProductNameLike("%" + query.productName() + "%");
        }
        if (query.productSpc() != null) {
            criteria.andProductSpcLike("%" + query.productSpc() + "%");
        }
        if (query.unitOfMeasure() != null) {
            criteria.andUnitOfMeasureLike("%" + query.unitOfMeasure() + "%");
        }
        if (query.quantity() != null) {
            criteria.andQuantityEqualTo( query.quantity());
        }
        if (query.quantityProduced() != null) {
            criteria.andQuantityProducedEqualTo( query.quantityProduced());
        }
        if (query.quantityChanged() != null) {
            criteria.andQuantityChangedEqualTo( query.quantityChanged());
        }
        if (query.quantityScheduled() != null) {
            criteria.andQuantityScheduledEqualTo( query.quantityScheduled());
        }
        if (query.clientId() != null) {
            criteria.andClientIdEqualTo( query.clientId());
        }
        if (query.clientCode() != null) {
            criteria.andClientCodeLike("%" + query.clientCode() + "%");
        }
        if (query.clientName() != null) {
            criteria.andClientNameLike("%" + query.clientName() + "%");
        }
        if (query.batchCode() != null) {
            criteria.andBatchCodeLike("%" + query.batchCode() + "%");
        }
        if (query.requestDate() != null) {
            criteria.andRequestDateEqualTo( query.requestDate());
        }
        if (query.parentId() != null) {
            criteria.andParentIdEqualTo( query.parentId());
        }
        if (query.ancestors() != null) {
            criteria.andAncestorsLike("%" + query.ancestors() + "%");
        }
        if (query.finishDate() != null) {
            criteria.andFinishDateEqualTo( query.finishDate());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        if (query.attr1() != null) {
            criteria.andAttr1Like("%" + query.attr1() + "%");
        }
        if (query.attr2() != null) {
            criteria.andAttr2Like("%" + query.attr2() + "%");
        }
        if (query.attr3() != null) {
            criteria.andAttr3EqualTo( query.attr3());
        }
        if (query.attr4() != null) {
            criteria.andAttr4EqualTo( query.attr4());
        }
        if (query.transactionDate() != null) {
            criteria.andTransactionDateEqualTo( query.transactionDate());
        }
        if (query.moId() != null) {
            criteria.andMoIdLike("%" + query.moId() + "%");
        }
        example.setOrderByClause("workorder_id");
        try (Page<ProWorkorderEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<ProWorkorderEntity> rows = mapper.selectByExample(example);
            return new ProWorkorderPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = ProWorkorderResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public ProWorkorderEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = ProWorkorderResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST)
    public ProWorkorderEntity insert(ProWorkorderCreateCommand input) {
        ProWorkorderEntity row = new ProWorkorderEntity();
        row.setWorkorderCode(input.workorderCode());
        row.setWorkorderName(input.workorderName());
        row.setOrderSource(input.orderSource());
        row.setSourceCode(input.sourceCode());
        row.setProductId(input.productId());
        row.setProductCode(input.productCode());
        row.setProductName(input.productName());
        row.setProductSpc(input.productSpc());
        row.setUnitOfMeasure(input.unitOfMeasure());
        row.setQuantity(input.quantity());
        row.setQuantityProduced(input.quantityProduced());
        row.setQuantityChanged(input.quantityChanged());
        row.setQuantityScheduled(input.quantityScheduled());
        row.setClientId(input.clientId());
        row.setClientCode(input.clientCode());
        row.setClientName(input.clientName());
        row.setBatchCode(input.batchCode());
        row.setRequestDate(input.requestDate());
        row.setParentId(input.parentId());
        row.setAncestors(input.ancestors());
        row.setFinishDate(input.finishDate());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        row.setAttr1(input.attr1());
        row.setAttr2(input.attr2());
        row.setAttr3(input.attr3());
        row.setAttr4(input.attr4());
        row.setTransactionDate(input.transactionDate());
        row.setMoId(input.moId());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getWorkorderId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = ProWorkorderResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public ProWorkorderEntity updateById(Long id, ProWorkorderUpdateCommand input) {
        ProWorkorderEntity row = requireRecord(id);
        row.setWorkorderId(id);
        row.setWorkorderCode(input.workorderCode());
        row.setWorkorderName(input.workorderName());
        row.setOrderSource(input.orderSource());
        row.setSourceCode(input.sourceCode());
        row.setProductId(input.productId());
        row.setProductCode(input.productCode());
        row.setProductName(input.productName());
        row.setProductSpc(input.productSpc());
        row.setUnitOfMeasure(input.unitOfMeasure());
        row.setQuantity(input.quantity());
        row.setQuantityProduced(input.quantityProduced());
        row.setQuantityChanged(input.quantityChanged());
        row.setQuantityScheduled(input.quantityScheduled());
        row.setClientId(input.clientId());
        row.setClientCode(input.clientCode());
        row.setClientName(input.clientName());
        row.setBatchCode(input.batchCode());
        row.setRequestDate(input.requestDate());
        row.setParentId(input.parentId());
        row.setAncestors(input.ancestors());
        row.setFinishDate(input.finishDate());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        row.setAttr1(input.attr1());
        row.setAttr2(input.attr2());
        row.setAttr3(input.attr3());
        row.setAttr4(input.attr4());
        row.setTransactionDate(input.transactionDate());
        row.setMoId(input.moId());
        if (mapper.updateByPrimaryKey(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = ProWorkorderResource.PREFIX,
            scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        ProWorkorderExample example = new ProWorkorderExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andWorkorderIdIn(ids)
                ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private ProWorkorderEntity requireRecord(Long id) {
        ProWorkorderEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
    public ProWorkorderSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.pro_workorder", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var result = self.getObject().upsert(scope, inputs);
            coordinator.progress(run, result.fetched());
            coordinator.succeed(run, null);
            return result;
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException deliveryFailure) {
                failure.addSuppressed(deliveryFailure);
            }
            throw failure;
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public ProWorkorderSyncResult upsert(FactoryScope scope,
                                       List<ExternalProWorkorder> inputs) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        if (inputs == null || inputs.size() > 1000) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var keys = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !keys.add(input.workorderCode())) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
        }
        if (inputs.isEmpty()) return new ProWorkorderSyncResult(0, 0, 0, 0);
        var example = new ProWorkorderExample();
        example.createCriteria().andWorkorderCodeIn(List.copyOf(keys))
                ;
        var existing = new HashMap<String, ProWorkorderEntity>();
        for (var row : mapper.selectByExample(example)) {
            if (existing.put(row.getWorkorderCode(), row) != null) {
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (var input : inputs) {
            var row = existing.get(input.workorderCode());
            if (row != null && same(row, input)) {
                skipped++;
                continue;
            }
            boolean insert = row == null;
            if (insert) {
                row = new ProWorkorderEntity();
            }
            row.setWorkorderCode(input.workorderCode());
            row.setWorkorderName(input.workorderName());
            row.setOrderSource(input.orderSource());
            row.setSourceCode(input.sourceCode());
            row.setProductId(input.productId());
            row.setProductCode(input.productCode());
            row.setProductName(input.productName());
            row.setProductSpc(input.productSpc());
            row.setUnitOfMeasure(input.unitOfMeasure());
            row.setQuantity(input.quantity());
            row.setQuantityProduced(input.quantityProduced());
            row.setQuantityChanged(input.quantityChanged());
            row.setQuantityScheduled(input.quantityScheduled());
            row.setClientId(input.clientId());
            row.setClientCode(input.clientCode());
            row.setClientName(input.clientName());
            row.setBatchCode(input.batchCode());
            row.setRequestDate(input.requestDate());
            row.setParentId(input.parentId());
            row.setAncestors(input.ancestors());
            row.setFinishDate(input.finishDate());
            row.setStatus(input.status());
            row.setRemark(input.remark());
            row.setAttr1(input.attr1());
            row.setAttr2(input.attr2());
            row.setAttr3(input.attr3());
            row.setAttr4(input.attr4());
            row.setTransactionDate(input.transactionDate());
            row.setMoId(input.moId());
            int affected = insert ? mapper.insertSelective(row) : mapper.updateByPrimaryKey(row);
            if (affected != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (insert) created++;
            else updated++;
        }
        if (updated > 0) {
            caches.evict(ProWorkorderResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        } else if (created > 0) {
            caches.evict(ProWorkorderResource.PREFIX,
                    BusinessCacheEvict.Scope.LIST);
        }
        return new ProWorkorderSyncResult(inputs.size(), created, updated, skipped);
    }

    private static boolean same(ProWorkorderEntity row, ExternalProWorkorder input) {
        return Objects.deepEquals(row.getWorkorderCode(), input.workorderCode())
                && Objects.deepEquals(row.getWorkorderName(), input.workorderName())
                && Objects.deepEquals(row.getOrderSource(), input.orderSource())
                && Objects.deepEquals(row.getSourceCode(), input.sourceCode())
                && Objects.deepEquals(row.getProductId(), input.productId())
                && Objects.deepEquals(row.getProductCode(), input.productCode())
                && Objects.deepEquals(row.getProductName(), input.productName())
                && Objects.deepEquals(row.getProductSpc(), input.productSpc())
                && Objects.deepEquals(row.getUnitOfMeasure(), input.unitOfMeasure())
                && Objects.deepEquals(row.getQuantity(), input.quantity())
                && Objects.deepEquals(row.getQuantityProduced(), input.quantityProduced())
                && Objects.deepEquals(row.getQuantityChanged(), input.quantityChanged())
                && Objects.deepEquals(row.getQuantityScheduled(), input.quantityScheduled())
                && Objects.deepEquals(row.getClientId(), input.clientId())
                && Objects.deepEquals(row.getClientCode(), input.clientCode())
                && Objects.deepEquals(row.getClientName(), input.clientName())
                && Objects.deepEquals(row.getBatchCode(), input.batchCode())
                && Objects.deepEquals(row.getRequestDate(), input.requestDate())
                && Objects.deepEquals(row.getParentId(), input.parentId())
                && Objects.deepEquals(row.getAncestors(), input.ancestors())
                && Objects.deepEquals(row.getFinishDate(), input.finishDate())
                && Objects.deepEquals(row.getStatus(), input.status())
                && Objects.deepEquals(row.getRemark(), input.remark())
                && Objects.deepEquals(row.getAttr1(), input.attr1())
                && Objects.deepEquals(row.getAttr2(), input.attr2())
                && Objects.deepEquals(row.getAttr3(), input.attr3())
                && Objects.deepEquals(row.getAttr4(), input.attr4())
                && Objects.deepEquals(row.getTransactionDate(), input.transactionDate())
                && Objects.deepEquals(row.getMoId(), input.moId());
    }
}

