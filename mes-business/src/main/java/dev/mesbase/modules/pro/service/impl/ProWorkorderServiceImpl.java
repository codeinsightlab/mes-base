// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.pro.dao.ProWorkorderMapper;
import dev.mesbase.modules.pro.domain.ProWorkorderCreateCommand;
import dev.mesbase.modules.pro.domain.ProWorkorderEntity;
import dev.mesbase.modules.pro.domain.ProWorkorderExample;
import dev.mesbase.modules.pro.domain.ProWorkorderPage;
import dev.mesbase.modules.pro.domain.ProWorkorderQueryDto;
import dev.mesbase.modules.pro.domain.ProWorkorderUpdateCommand;
import dev.mesbase.modules.pro.service.IProWorkorderService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产工单表 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProWorkorderServiceImpl implements IProWorkorderService {
    private final ProWorkorderMapper mapper;

    public ProWorkorderServiceImpl(ProWorkorderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
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
            criteria.andProductIdEqualTo(query.productId());
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
            criteria.andQuantityEqualTo(query.quantity());
        }
        if (query.quantityProduced() != null) {
            criteria.andQuantityProducedEqualTo(query.quantityProduced());
        }
        if (query.quantityChanged() != null) {
            criteria.andQuantityChangedEqualTo(query.quantityChanged());
        }
        if (query.quantityScheduled() != null) {
            criteria.andQuantityScheduledEqualTo(query.quantityScheduled());
        }
        if (query.clientId() != null) {
            criteria.andClientIdEqualTo(query.clientId());
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
            criteria.andRequestDateEqualTo(query.requestDate());
        }
        if (query.parentId() != null) {
            criteria.andParentIdEqualTo(query.parentId());
        }
        if (query.ancestors() != null) {
            criteria.andAncestorsLike("%" + query.ancestors() + "%");
        }
        if (query.finishDate() != null) {
            criteria.andFinishDateEqualTo(query.finishDate());
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
            criteria.andAttr3EqualTo(query.attr3());
        }
        if (query.attr4() != null) {
            criteria.andAttr4EqualTo(query.attr4());
        }
        if (query.transactionDate() != null) {
            criteria.andTransactionDateEqualTo(query.transactionDate());
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
    public ProWorkorderEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
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
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        ProWorkorderExample example = new ProWorkorderExample();
        example.createCriteria().andWorkorderIdIn(ids);
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
}

