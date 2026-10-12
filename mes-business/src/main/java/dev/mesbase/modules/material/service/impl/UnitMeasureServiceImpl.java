// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.material.dao.UnitMeasureMapper;
import dev.mesbase.modules.material.domain.UnitMeasureCreateCommand;
import dev.mesbase.modules.material.domain.UnitMeasureEntity;
import dev.mesbase.modules.material.domain.UnitMeasureExample;
import dev.mesbase.modules.material.domain.UnitMeasurePage;
import dev.mesbase.modules.material.domain.UnitMeasureQueryDto;
import dev.mesbase.modules.material.domain.UnitMeasureUpdateCommand;
import dev.mesbase.modules.material.service.IUnitMeasureService;
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
 * 计量单位 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class UnitMeasureServiceImpl implements IUnitMeasureService {
    private final UnitMeasureMapper mapper;
    private final BasicDataRules rules;

    public UnitMeasureServiceImpl(UnitMeasureMapper mapper, BasicDataRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public UnitMeasurePage selectPage(UnitMeasureQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        UnitMeasureExample example = new UnitMeasureExample();
        UnitMeasureExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.symbol() != null) {
            criteria.andSymbolLike("%" + query.symbol() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        example.setOrderByClause("id");
        try (Page<UnitMeasureEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<UnitMeasureEntity> rows = mapper.selectByExample(example);
            return new UnitMeasurePage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public UnitMeasureEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public UnitMeasureEntity insert(UnitMeasureCreateCommand input) {
        UnitMeasureEntity row = new UnitMeasureEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setSymbol(input.symbol());
        row.setStatus(input.status());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public UnitMeasureEntity updateById(Long id, UnitMeasureUpdateCommand input) {
        UnitMeasureEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setSymbol(input.symbol());
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
        rules.deletingUnitMeasure(ids);
        UnitMeasureExample example = new UnitMeasureExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(UnitMeasureEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(UnitMeasureEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private UnitMeasureEntity requireRecord(Long id) {
        UnitMeasureEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

