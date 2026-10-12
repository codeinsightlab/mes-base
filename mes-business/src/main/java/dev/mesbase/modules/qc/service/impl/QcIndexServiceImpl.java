// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.qc.dao.QcIndexMapper;
import dev.mesbase.modules.qc.domain.QcIndexCreateCommand;
import dev.mesbase.modules.qc.domain.QcIndexEntity;
import dev.mesbase.modules.qc.domain.QcIndexExample;
import dev.mesbase.modules.qc.domain.QcIndexPage;
import dev.mesbase.modules.qc.domain.QcIndexQueryDto;
import dev.mesbase.modules.qc.domain.QcIndexUpdateCommand;
import dev.mesbase.modules.qc.service.IQcIndexService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质量检测项 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcIndexServiceImpl implements IQcIndexService {
    private final QcIndexMapper mapper;

    public QcIndexServiceImpl(QcIndexMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public QcIndexPage selectPage(QcIndexQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        QcIndexExample example = new QcIndexExample();
        QcIndexExample.Criteria criteria = example.createCriteria();
        if (query.factoryId() != null) {
            criteria.andFactoryIdLike("%" + query.factoryId() + "%");
        }
        if (query.indexCode() != null) {
            criteria.andIndexCodeLike("%" + query.indexCode() + "%");
        }
        if (query.indexName() != null) {
            criteria.andIndexNameLike("%" + query.indexName() + "%");
        }
        if (query.indexType() != null) {
            criteria.andIndexTypeLike("%" + query.indexType() + "%");
        }
        if (query.qcTool() != null) {
            criteria.andQcToolLike("%" + query.qcTool() + "%");
        }
        if (query.remark() != null) {
            criteria.andRemarkLike("%" + query.remark() + "%");
        }
        example.setOrderByClause("index_id");
        try (Page<QcIndexEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<QcIndexEntity> rows = mapper.selectByExample(example);
            return new QcIndexPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public QcIndexEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public QcIndexEntity insert(QcIndexCreateCommand input) {
        QcIndexEntity row = new QcIndexEntity();
        row.setFactoryId(input.factoryId());
        row.setIndexCode(input.indexCode());
        row.setIndexName(input.indexName());
        row.setIndexType(input.indexType());
        row.setQcTool(input.qcTool());
        row.setRemark(input.remark());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getIndexId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public QcIndexEntity updateById(Long id, QcIndexUpdateCommand input) {
        QcIndexEntity row = requireRecord(id);
        row.setIndexId(id);
        row.setFactoryId(input.factoryId());
        row.setIndexCode(input.indexCode());
        row.setIndexName(input.indexName());
        row.setIndexType(input.indexType());
        row.setQcTool(input.qcTool());
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
        QcIndexExample example = new QcIndexExample();
        example.createCriteria().andIndexIdIn(ids);
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private QcIndexEntity requireRecord(Long id) {
        QcIndexEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

