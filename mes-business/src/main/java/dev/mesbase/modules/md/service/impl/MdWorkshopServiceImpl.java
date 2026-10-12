// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.md.dao.MdWorkshopMapper;
import dev.mesbase.modules.md.domain.MdWorkshopCreateCommand;
import dev.mesbase.modules.md.domain.MdWorkshopEntity;
import dev.mesbase.modules.md.domain.MdWorkshopExample;
import dev.mesbase.modules.md.domain.MdWorkshopPage;
import dev.mesbase.modules.md.domain.MdWorkshopQueryDto;
import dev.mesbase.modules.md.domain.MdWorkshopUpdateCommand;
import dev.mesbase.modules.md.service.IMdWorkshopService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 车间 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdWorkshopServiceImpl implements IMdWorkshopService {
    private final MdWorkshopMapper mapper;

    public MdWorkshopServiceImpl(MdWorkshopMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MdWorkshopPage selectPage(MdWorkshopQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        MdWorkshopExample example = new MdWorkshopExample();
        MdWorkshopExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.workshopCode() != null) {
            criteria.andWorkshopCodeLike("%" + query.workshopCode() + "%");
        }
        if (query.workshopName() != null) {
            criteria.andWorkshopNameLike("%" + query.workshopName() + "%");
        }
        if (query.area() != null) {
            criteria.andAreaEqualTo(query.area());
        }
        if (query.charge() != null) {
            criteria.andChargeLike("%" + query.charge() + "%");
        }
        if (query.deptId() != null) {
            criteria.andDeptIdEqualTo(query.deptId());
        }
        if (query.enableFlag() != null) {
            criteria.andEnableFlagLike("%" + query.enableFlag() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("workshop_id");
        try (Page<MdWorkshopEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<MdWorkshopEntity> rows = mapper.selectByExample(example);
            return new MdWorkshopPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public MdWorkshopEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MdWorkshopEntity insert(MdWorkshopCreateCommand input) {
        MdWorkshopEntity row = new MdWorkshopEntity();
        row.setFactoryId(input.factoryId());
        row.setWorkshopCode(input.workshopCode());
        row.setWorkshopName(input.workshopName());
        row.setArea(input.area());
        row.setCharge(input.charge());
        row.setDeptId(input.deptId());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getWorkshopId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public MdWorkshopEntity updateById(Long id, MdWorkshopUpdateCommand input) {
        MdWorkshopEntity row = requireRecord(id);
        row.setWorkshopId(id);
        row.setFactoryId(input.factoryId());
        row.setWorkshopCode(input.workshopCode());
        row.setWorkshopName(input.workshopName());
        row.setArea(input.area());
        row.setCharge(input.charge());
        row.setDeptId(input.deptId());
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
        MdWorkshopExample example = new MdWorkshopExample();
        example.createCriteria().andWorkshopIdIn(ids);
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private MdWorkshopEntity requireRecord(Long id) {
        MdWorkshopEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

