// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.material.dao.SafetyStockMapper;
import dev.mesbase.modules.material.domain.SafetyStockCreateCommand;
import dev.mesbase.modules.material.domain.SafetyStockEntity;
import dev.mesbase.modules.material.domain.SafetyStockExample;
import dev.mesbase.modules.material.domain.SafetyStockPage;
import dev.mesbase.modules.material.domain.SafetyStockQueryDto;
import dev.mesbase.modules.material.domain.SafetyStockUpdateCommand;
import dev.mesbase.modules.material.service.ISafetyStockService;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import dev.mesbase.modules.material.service.BasicDataRules;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 安全库存配置 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class SafetyStockServiceImpl implements ISafetyStockService {
    private final SafetyStockMapper mapper;
    private final BasicDataRules rules;

    public SafetyStockServiceImpl(SafetyStockMapper mapper, BasicDataRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public SafetyStockPage selectPage(SafetyStockQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        SafetyStockExample example = new SafetyStockExample();
        SafetyStockExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.minQty() != null) {
            criteria.andMinQtyEqualTo(query.minQty());
        }
        if (query.maxQty() != null) {
            criteria.andMaxQtyEqualTo(query.maxQty());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        example.setOrderByClause("id");
        try (Page<SafetyStockEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<SafetyStockEntity> rows = mapper.selectByExample(example);
            return new SafetyStockPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public SafetyStockEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public SafetyStockEntity insert(SafetyStockCreateCommand input) {
        SafetyStockEntity row = new SafetyStockEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setMaterialId(input.materialId());
        row.setMinQty(input.minQty());
        row.setMaxQty(input.maxQty());
        row.setStatus(input.status());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public SafetyStockEntity updateById(Long id, SafetyStockUpdateCommand input) {
        SafetyStockEntity row = requireRecord(id);
        row.setId(id);
        row.setMaterialId(input.materialId());
        row.setMinQty(input.minQty());
        row.setMaxQty(input.maxQty());
        row.setStatus(input.status());
        rules.validate(row);
        if (updateUnique(row) != 1) {
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
        rules.deletingSafetyStock(ids);
        SafetyStockExample example = new SafetyStockExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(SafetyStockEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(SafetyStockEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private SafetyStockEntity requireRecord(Long id) {
        SafetyStockEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

