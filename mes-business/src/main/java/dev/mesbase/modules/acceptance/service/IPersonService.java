// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.acceptance.service;

import dev.mesbase.modules.acceptance.domain.PersonCreateCommand;
import dev.mesbase.modules.acceptance.domain.PersonEntity;
import dev.mesbase.modules.acceptance.domain.PersonPage;
import dev.mesbase.modules.acceptance.domain.PersonQueryDto;
import dev.mesbase.modules.acceptance.domain.PersonUpdateCommand;
import java.util.List;

/**
 * 人员 CRUD 用例。
 *
 * @author MES Base
 */
public interface IPersonService {
    PersonPage selectPage(PersonQueryDto query);

    PersonEntity selectById(Long id);

    PersonEntity insert(PersonCreateCommand input);

    PersonEntity updateById(Long id, PersonUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

