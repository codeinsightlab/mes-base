package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.OperationCreateCommand;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationQueryDto;
import dev.mesbase.business.production.domain.OperationUpdateCommand;
import java.util.List;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.business.production.sync.ExternalOperation;
import dev.mesbase.business.production.sync.ProductionSyncResult;

/**
 * 工序 CRUD 用例。
 *
 * @author MES Base
 */
public interface IOperationService {
    Page<OperationEntity> selectPage(OperationQueryDto query);

    OperationEntity selectById(Long id);

    OperationEntity insert(OperationCreateCommand input);

    OperationEntity updateById(Long id, OperationUpdateCommand input);

    void deleteByIds(List<Long> ids);

    ProductionSyncResult sync(FactoryScope scope);

    ProductionSyncResult upsert(FactoryScope scope, List<ExternalOperation> inputs);
}

