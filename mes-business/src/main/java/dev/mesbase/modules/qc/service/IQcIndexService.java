// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.service;

import dev.mesbase.modules.qc.domain.QcIndexCreateCommand;
import dev.mesbase.modules.qc.domain.QcIndexEntity;
import dev.mesbase.modules.qc.domain.QcIndexPage;
import dev.mesbase.modules.qc.domain.QcIndexQueryDto;
import dev.mesbase.modules.qc.domain.QcIndexUpdateCommand;
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
}

