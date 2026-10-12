// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.service;

import dev.mesbase.modules.pro.domain.ProWorkorderCreateCommand;
import dev.mesbase.modules.pro.domain.ProWorkorderEntity;
import dev.mesbase.modules.pro.domain.ProWorkorderPage;
import dev.mesbase.modules.pro.domain.ProWorkorderQueryDto;
import dev.mesbase.modules.pro.domain.ProWorkorderUpdateCommand;
import java.util.List;

/**
 * 生产工单 CRUD 用例。
 *
 * @author MES Base
 */
public interface IProWorkorderService {
    ProWorkorderPage selectPage(ProWorkorderQueryDto query);

    ProWorkorderEntity selectById(Long id);

    ProWorkorderEntity insert(ProWorkorderCreateCommand input);

    ProWorkorderEntity updateById(Long id, ProWorkorderUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

