// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.service;

import dev.mesbase.business.qc.domain.QcIndexCreateCommand;
import dev.mesbase.business.qc.domain.QcIndexEntity;
import dev.mesbase.business.qc.domain.QcIndexPage;
import dev.mesbase.business.qc.domain.QcIndexQueryDto;
import dev.mesbase.business.qc.domain.QcIndexUpdateCommand;
import dev.mesbase.business.qc.sync.ExternalQcIndex;
import dev.mesbase.business.qc.sync.QcIndexSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 质量检测项 CRUD 用例。
 *
 * @author MES Base
 */
public interface IQcIndexService {
    QcIndexPage selectPage(QcIndexQueryDto query);

    QcIndexEntity selectById(Long id);

    QcIndexEntity insert(QcIndexCreateCommand input);

    QcIndexEntity updateById(Long id, QcIndexUpdateCommand input);

    void deleteByIds(List<Long> ids);
    QcIndexSyncResult sync(FactoryScope scope);

    QcIndexSyncResult upsert(FactoryScope scope,
            List<ExternalQcIndex> inputs);
}

