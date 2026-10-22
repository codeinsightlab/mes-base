package dev.mesbase.business.production.service.impl;

import dev.mesbase.business.production.domain.MesWorkorderMaterialPage;
import dev.mesbase.common.validation.ParameterChecks;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.production.domain.MesWorkorderMaterialQueryDto;
import dev.mesbase.business.production.domain.MesWorkorderMaterialResource;
import dev.mesbase.business.production.service.IMesWorkorderMaterialService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地工单用料需求 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.production.closure.internal-crud.enabled", havingValue = "true")
public class MesWorkorderMaterialServiceImpl implements IMesWorkorderMaterialService {
    private final MesWorkorderMaterialMapper mapper;

    public MesWorkorderMaterialServiceImpl(MesWorkorderMaterialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesWorkorderMaterialResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public MesWorkorderMaterialPage selectPage(MesWorkorderMaterialQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        ParameterChecks.paging(offset, limit, 100);
        MesWorkorderMaterialExample example = new MesWorkorderMaterialExample();
        MesWorkorderMaterialExample.Criteria criteria = example.createCriteria();
        if (query.materialId() != null) {
            criteria.andMaterialIdEqualTo(query.materialId());
        }
        if (query.unitId() != null) {
            criteria.andUnitIdEqualTo(query.unitId());
        }
        if (query.totalRequiredQty() != null) {
            criteria.andTotalRequiredQtyEqualTo(query.totalRequiredQty());
        }
        if (query.plannedQty() != null) {
            criteria.andPlannedQtyEqualTo(query.plannedQty());
        }
        if (query.warehouseIds() != null) {
            criteria.andWarehouseIdsLike("%" + query.warehouseIds() + "%");
        }
        if (query.workCenter() != null) {
            criteria.andWorkCenterLike("%" + query.workCenter() + "%");
        }
        if (query.revision() != null) {
            criteria.andRevisionEqualTo(query.revision());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.updatedBy() != null) {
            criteria.andUpdatedByEqualTo(query.updatedBy());
        }
        if (query.updatedAt() != null) {
            criteria.andUpdatedAtEqualTo(query.updatedAt());
        }
        example.setOrderByClause("id");
        try (Page<MesWorkorderMaterialEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MesWorkorderMaterialEntity> rows = mapper.selectByExample(example);
            return new MesWorkorderMaterialPage(rows, page.getTotal(), offset, limit);
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = MesWorkorderMaterialResource.PREFIX,
            type = BusinessCache.Type.DETAIL)
    public MesWorkorderMaterialEntity selectById(Long id) {
        return requireRecord(id);
    }


    private MesWorkorderMaterialEntity requireRecord(Long id) {
        MesWorkorderMaterialEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

