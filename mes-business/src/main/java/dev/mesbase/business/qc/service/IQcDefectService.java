// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.qc.service;

import dev.mesbase.business.qc.domain.QcDefectCreateCommand;
import dev.mesbase.business.qc.domain.QcDefectEntity;
import dev.mesbase.business.qc.domain.QcDefectPage;
import dev.mesbase.business.qc.domain.QcDefectQueryDto;
import dev.mesbase.business.qc.domain.QcDefectUpdateCommand;
import dev.mesbase.business.qc.sync.ExternalQcDefect;
import dev.mesbase.business.qc.sync.QcDefectSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 质量缺陷 CRUD 用例。
 *
 * @author MES Base
 */
public interface IQcDefectService {
    QcDefectPage selectPage(QcDefectQueryDto query);

    QcDefectEntity selectById(Long id);

    QcDefectEntity insert(QcDefectCreateCommand input);

    QcDefectEntity updateById(Long id, QcDefectUpdateCommand input);

    void deleteByIds(List<Long> ids);
    QcDefectSyncResult sync(FactoryScope scope);

    QcDefectSyncResult upsert(FactoryScope scope,
            List<ExternalQcDefect> inputs);
}

