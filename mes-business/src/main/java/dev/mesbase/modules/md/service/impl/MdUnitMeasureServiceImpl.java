// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.md.dao.MdUnitMeasureMapper;
import dev.mesbase.modules.md.domain.MdUnitMeasureCreateCommand;
import dev.mesbase.modules.md.domain.MdUnitMeasureEntity;
import dev.mesbase.modules.md.domain.MdUnitMeasureExample;
import dev.mesbase.modules.md.domain.MdUnitMeasurePage;
import dev.mesbase.modules.md.domain.MdUnitMeasureQueryDto;
import dev.mesbase.modules.md.domain.MdUnitMeasureUpdateCommand;
import dev.mesbase.modules.md.service.IMdUnitMeasureService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计量单位 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdUnitMeasureServiceImpl implements IMdUnitMeasureService {
    private final MdUnitMeasureMapper mapper;

    public MdUnitMeasureServiceImpl(MdUnitMeasureMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MdUnitMeasurePage selectPage(MdUnitMeasureQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MdUnitMeasureExample example = new MdUnitMeasureExample();
        MdUnitMeasureExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.measureCode() != null) {
            criteria.andMeasureCodeLike("%" + query.measureCode() + "%");
        }
        if (query.measureName() != null) {
            criteria.andMeasureNameLike("%" + query.measureName() + "%");
        }
        if (query.primaryFlag() != null) {
            criteria.andPrimaryFlagLike("%" + query.primaryFlag() + "%");
        }
        if (query.primaryId() != null) {
            criteria.andPrimaryIdEqualTo(query.primaryId());
        }
        if (query.changeRate() != null) {
            criteria.andChangeRateEqualTo(query.changeRate());
        }
        if (query.enableFlag() != null) {
            criteria.andEnableFlagLike("%" + query.enableFlag() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("measure_id");
        try (Page<MdUnitMeasureEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MdUnitMeasureEntity> rows = mapper.selectByExample(example);
            return new MdUnitMeasurePage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MdUnitMeasureEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MdUnitMeasureEntity insert(MdUnitMeasureCreateCommand input) {
        MdUnitMeasureEntity row = new MdUnitMeasureEntity();
        row.setFactoryId(input.factoryId());
        row.setMeasureCode(input.measureCode());
        row.setMeasureName(input.measureName());
        row.setPrimaryFlag(input.primaryFlag());
        row.setPrimaryId(input.primaryId());
        row.setChangeRate(input.changeRate());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getMeasureId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MdUnitMeasureEntity updateById(Long id, MdUnitMeasureUpdateCommand input) {
        MdUnitMeasureEntity row = requireRecord(id);
        row.setMeasureId(id);
        row.setFactoryId(input.factoryId());
        row.setMeasureCode(input.measureCode());
        row.setMeasureName(input.measureName());
        row.setPrimaryFlag(input.primaryFlag());
        row.setPrimaryId(input.primaryId());
        row.setChangeRate(input.changeRate());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
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
        MdUnitMeasureExample example = new MdUnitMeasureExample();
        example.createCriteria().andMeasureIdIn(ids);
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MdUnitMeasureEntity requireRecord(Long id) {
        MdUnitMeasureEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

