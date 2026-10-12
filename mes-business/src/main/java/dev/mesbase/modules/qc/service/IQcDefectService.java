// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.service;

import dev.mesbase.modules.qc.domain.QcDefectCreateCommand;
import dev.mesbase.modules.qc.domain.QcDefectEntity;
import dev.mesbase.modules.qc.domain.QcDefectPage;
import dev.mesbase.modules.qc.domain.QcDefectQueryDto;
import dev.mesbase.modules.qc.domain.QcDefectUpdateCommand;
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
}

