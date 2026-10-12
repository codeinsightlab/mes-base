// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.service.impl;

import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.material.dao.MaterialMapper;
import dev.mesbase.modules.material.domain.MaterialCreateCommand;
import dev.mesbase.modules.material.domain.MaterialEntity;
import dev.mesbase.modules.material.domain.MaterialResource;
import dev.mesbase.modules.material.domain.MaterialExample;
import dev.mesbase.modules.material.domain.MaterialPage;
import dev.mesbase.modules.material.domain.MaterialQueryDto;
import dev.mesbase.modules.material.domain.MaterialUpdateCommand;
import dev.mesbase.modules.material.service.IMaterialService;
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
 * 物料产品 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialServiceImpl implements IMaterialService {
    private final MaterialMapper mapper;
    private final BasicDataRules rules;

    public MaterialServiceImpl(MaterialMapper mapper, BasicDataRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MaterialResource.PREFIX, type = BusinessCache.Type.LIST)
    public MaterialPage selectPage(MaterialQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MaterialExample example = new MaterialExample();
        MaterialExample.Criteria criteria = example.createCriteria();
        criteria.andFactoryIdEqualTo(FactoryContext.required().factoryId().value());
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.specification() != null) {
            criteria.andSpecificationLike("%" + query.specification() + "%");
        }
        if (query.categoryId() != null) {
            criteria.andCategoryIdEqualTo(query.categoryId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo(query.unitId());
        }
        if (query.materialType() != null) {
            criteria.andMaterialTypeLike("%" + query.materialType() + "%");
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.externalId() != null) {
            criteria.andExternalIdLike("%" + query.externalId() + "%");
        }
        if (query.externalCode() != null) {
            criteria.andExternalCodeLike("%" + query.externalCode() + "%");
        }
        example.setOrderByClause("id");
        try (Page<MaterialEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MaterialEntity> rows = mapper.selectByExample(example);
            return new MaterialPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MaterialResource.PREFIX, type = BusinessCache.Type.DETAIL)
    public MaterialEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST)
    public MaterialEntity insert(MaterialCreateCommand input) {
        MaterialEntity row = new MaterialEntity();
        row.setFactoryId(FactoryContext.required().factoryId().value());
        row.setCode(input.code());
        row.setName(input.name());
        row.setSpecification(input.specification());
        row.setCategoryId(input.categoryId());
        row.setUnitId(input.unitId());
        row.setMaterialType(input.materialType());
        row.setStatus(input.status());
        row.setExternalId(input.externalId());
        row.setExternalCode(input.externalCode());
        rules.validate(row);
        if (insertUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MaterialEntity updateById(Long id, MaterialUpdateCommand input) {
        MaterialEntity row = requireRecord(id);
        row.setId(id);
        row.setCode(input.code());
        row.setName(input.name());
        row.setSpecification(input.specification());
        row.setCategoryId(input.categoryId());
        row.setUnitId(input.unitId());
        row.setMaterialType(input.materialType());
        row.setStatus(input.status());
        row.setExternalId(input.externalId());
        row.setExternalCode(input.externalCode());
        rules.validate(row);
        if (updateUnique(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MaterialResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        rules.deletingMaterial(ids);
        MaterialExample example = new MaterialExample();
        for (var id : ids) requireRecord(id);
        example.createCriteria().andIdIn(ids)
                .andFactoryIdEqualTo(FactoryContext.required().factoryId().value())
        ;
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private int insertUnique(MaterialEntity row) {
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private int updateUnique(MaterialEntity row) {
        try {
            return mapper.updateByPrimaryKey(row);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private MaterialEntity requireRecord(Long id) {
        MaterialEntity row = mapper.selectByPrimaryKey(id);
        if (row == null || !FactoryContext.required().factoryId().value().equals(row.getFactoryId())) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

