// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.qc.dao.QcDefectMapper;
import dev.mesbase.modules.qc.domain.QcDefectCreateCommand;
import dev.mesbase.modules.qc.domain.QcDefectEntity;
import dev.mesbase.modules.qc.domain.QcDefectExample;
import dev.mesbase.modules.qc.domain.QcDefectPage;
import dev.mesbase.modules.qc.domain.QcDefectQueryDto;
import dev.mesbase.modules.qc.domain.QcDefectUpdateCommand;
import dev.mesbase.modules.qc.service.IQcDefectService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质量缺陷 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcDefectServiceImpl implements IQcDefectService {
    private final QcDefectMapper mapper;

    public QcDefectServiceImpl(QcDefectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public QcDefectPage selectPage(QcDefectQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        QcDefectExample example = new QcDefectExample();
        QcDefectExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.defectCode() != null) {
            criteria.andDefectCodeLike("%" + query.defectCode() + "%");
        }
        if (query.defectName() != null) {
            criteria.andDefectNameLike("%" + query.defectName() + "%");
        }
        if (query.indexType() != null) {
            criteria.andIndexTypeLike("%" + query.indexType() + "%");
        }
        if (query.defectLevel() != null) {
            criteria.andDefectLevelLike("%" + query.defectLevel() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("defect_id");
        try (Page<QcDefectEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<QcDefectEntity> rows = mapper.selectByExample(example);
            return new QcDefectPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public QcDefectEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public QcDefectEntity insert(QcDefectCreateCommand input) {
        QcDefectEntity row = new QcDefectEntity();
        row.setFactoryId(input.factoryId());
        row.setDefectCode(input.defectCode());
        row.setDefectName(input.defectName());
        row.setIndexType(input.indexType());
        row.setDefectLevel(input.defectLevel());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getDefectId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public QcDefectEntity updateById(Long id, QcDefectUpdateCommand input) {
        QcDefectEntity row = requireRecord(id);
        row.setDefectId(id);
        row.setFactoryId(input.factoryId());
        row.setDefectCode(input.defectCode());
        row.setDefectName(input.defectName());
        row.setIndexType(input.indexType());
        row.setDefectLevel(input.defectLevel());
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
        QcDefectExample example = new QcDefectExample();
        example.createCriteria().andDefectIdIn(ids);
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private QcDefectEntity requireRecord(Long id) {
        QcDefectEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

