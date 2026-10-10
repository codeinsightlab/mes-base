// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.acceptance.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.modules.acceptance.dao.PersonMapper;
import dev.mesbase.modules.acceptance.domain.PersonCreateCommand;
import dev.mesbase.modules.acceptance.domain.PersonEntity;
import dev.mesbase.modules.acceptance.domain.PersonExample;
import dev.mesbase.modules.acceptance.domain.PersonPage;
import dev.mesbase.modules.acceptance.domain.PersonQueryDto;
import dev.mesbase.modules.acceptance.domain.PersonUpdateCommand;
import dev.mesbase.modules.acceptance.service.IPersonService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人员 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class PersonServiceImpl implements IPersonService {
    private final PersonMapper mapper;

    public PersonServiceImpl(PersonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public PersonPage selectPage(PersonQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        PersonExample example = new PersonExample();
        PersonExample.Criteria criteria = example.createCriteria();
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        example.setOrderByClause("id");
        try (Page<PersonEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<PersonEntity> rows = mapper.selectByExample(example);
            return new PersonPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public PersonEntity selectById(Long id) {
        return requireRecord(id);
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public PersonEntity insert(PersonCreateCommand input) {
        PersonEntity row = new PersonEntity();
        row.setName(input.name());
        if (mapper.insert(row) != 1) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return requireRecord(row.getId());
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public PersonEntity updateById(Long id, PersonUpdateCommand input) {
        requireRecord(id);
        PersonEntity row = new PersonEntity();
        row.setId(id);
        row.setName(input.name());
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
        PersonExample example = new PersonExample();
        example.createCriteria().andIdIn(ids);
        if (mapper.deleteByExample(example) != ids.size()) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private PersonEntity requireRecord(Long id) {
        PersonEntity row = mapper.selectByPrimaryKey(id);
        if (row == null) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        return row;
    }
}

