// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.service;

import dev.mesbase.modules.dv.domain.DvSubjectCreateCommand;
import dev.mesbase.modules.dv.domain.DvSubjectEntity;
import dev.mesbase.modules.dv.domain.DvSubjectPage;
import dev.mesbase.modules.dv.domain.DvSubjectQueryDto;
import dev.mesbase.modules.dv.domain.DvSubjectUpdateCommand;
import java.util.List;

/**
 * 设备点检保养项目 CRUD 用例。
 *
 * @author MES Base
 */
public interface IDvSubjectService {
    DvSubjectPage selectPage(DvSubjectQueryDto query);

    DvSubjectEntity selectById(Long id);

    DvSubjectEntity insert(DvSubjectCreateCommand input);

    DvSubjectEntity updateById(Long id, DvSubjectUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

