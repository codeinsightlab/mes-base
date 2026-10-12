// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.dv.dao.DvSubjectMapper;
import dev.mesbase.modules.dv.domain.DvSubjectCreateCommand;
import dev.mesbase.modules.dv.domain.DvSubjectEntity;
import dev.mesbase.modules.dv.domain.DvSubjectExample;
import dev.mesbase.modules.dv.domain.DvSubjectPage;
import dev.mesbase.modules.dv.domain.DvSubjectQueryDto;
import dev.mesbase.modules.dv.domain.DvSubjectUpdateCommand;
import dev.mesbase.modules.dv.service.IDvSubjectService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备点检保养项目 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class DvSubjectServiceImpl implements IDvSubjectService {
    private final DvSubjectMapper mapper;

    public DvSubjectServiceImpl(DvSubjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DvSubjectPage selectPage(DvSubjectQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        DvSubjectExample example = new DvSubjectExample();
        DvSubjectExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.subjectCode() != null) {
            criteria.andSubjectCodeLike("%" + query.subjectCode() + "%");
        }
        if (query.subjectName() != null) {
            criteria.andSubjectNameLike("%" + query.subjectName() + "%");
        }
        if (query.subjectType() != null) {
            criteria.andSubjectTypeLike("%" + query.subjectType() + "%");
        }
        if (query.subjectContent() != null) {
            criteria.andSubjectContentLike("%" + query.subjectContent() + "%");
        }
        if (query.subjectStandard() != null) {
            criteria.andSubjectStandardLike("%" + query.subjectStandard() + "%");
        }
        if (query.enableFlag() != null) {
            criteria.andEnableFlagLike("%" + query.enableFlag() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("subject_id");
        try (Page<DvSubjectEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<DvSubjectEntity> rows = mapper.selectByExample(example);
            return new DvSubjectPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public DvSubjectEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public DvSubjectEntity insert(DvSubjectCreateCommand input) {
        DvSubjectEntity row = new DvSubjectEntity();
        row.setFactoryId(input.factoryId());
        row.setSubjectCode(input.subjectCode());
        row.setSubjectName(input.subjectName());
        row.setSubjectType(input.subjectType());
        row.setSubjectContent(input.subjectContent());
        row.setSubjectStandard(input.subjectStandard());
        row.setEnableFlag(input.enableFlag());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getSubjectId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public DvSubjectEntity updateById(Long id, DvSubjectUpdateCommand input) {
        DvSubjectEntity row = requireRecord(id);
        row.setSubjectId(id);
        row.setFactoryId(input.factoryId());
        row.setSubjectCode(input.subjectCode());
        row.setSubjectName(input.subjectName());
        row.setSubjectType(input.subjectType());
        row.setSubjectContent(input.subjectContent());
        row.setSubjectStandard(input.subjectStandard());
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
        DvSubjectExample example = new DvSubjectExample();
        example.createCriteria().andSubjectIdIn(ids);
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private DvSubjectEntity requireRecord(Long id) {
        DvSubjectEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

