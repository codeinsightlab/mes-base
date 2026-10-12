// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.material.dao.MaterialCategoryMapper;
import dev.mesbase.modules.material.domain.MaterialCategoryCreateCommand;
import dev.mesbase.modules.material.domain.MaterialCategoryEntity;
import dev.mesbase.modules.material.domain.MaterialCategoryExample;
import dev.mesbase.modules.material.domain.MaterialCategoryPage;
import dev.mesbase.modules.material.domain.MaterialCategoryQueryDto;
import dev.mesbase.modules.material.domain.MaterialCategoryUpdateCommand;
import dev.mesbase.modules.material.service.IMaterialCategoryService;
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
 * 物料产品分类 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialCategoryServiceImpl implements IMaterialCategoryService {
    private final MaterialCategoryMapper mapper;
    private final BasicDataRules rules;

    public MaterialCategoryServiceImpl(MaterialCategoryMapper mapper, BasicDataRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialCategoryPage selectPage(MaterialCategoryQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MaterialCategoryExample example = new MaterialCategoryExample();
        MaterialCategoryExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.parentId() != null) {
            criteria.andParentIdEqualTo(query.parentId());
        }
        if (query.sort() != null) {
            criteria.andSortEqualTo(query.sort());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("id");
        try (Page<MaterialCategoryEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MaterialCategoryEntity> rows = mapper.selectByExample(example);
            return new MaterialCategoryPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MaterialCategoryEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MaterialCategoryEntity insert(MaterialCategoryCreateCommand input) {
        MaterialCategoryEntity row = new MaterialCategoryEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setParentId(input.parentId());
        row.setSort(input.sort());
        row.setStatus(input.status());
        row.setRemark(input.remark());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MaterialCategoryEntity updateById(Long id, MaterialCategoryUpdateCommand input) {
        MaterialCategoryEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setParentId(input.parentId());
        row.setSort(input.sort());
        row.setStatus(input.status());
        row.setRemark(input.remark());
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
        rules.deletingMaterialCategory(ids);
        MaterialCategoryExample example = new MaterialCategoryExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(MaterialCategoryEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(MaterialCategoryEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private MaterialCategoryEntity requireRecord(Long id) {
        MaterialCategoryEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

