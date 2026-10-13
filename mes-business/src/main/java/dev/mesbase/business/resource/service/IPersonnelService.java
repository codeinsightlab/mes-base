// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.service;

import dev.mesbase.business.resource.domain.PersonnelCreateCommand;
import dev.mesbase.business.resource.domain.PersonnelEntity;
import dev.mesbase.business.resource.domain.PersonnelPage;
import dev.mesbase.business.resource.domain.PersonnelQueryDto;
import dev.mesbase.business.resource.domain.PersonnelUpdateCommand;
import java.util.List;

/**
 * 生产人员 CRUD 用例。
 *
 * @author MES Base
 */
public interface IPersonnelService {
    PersonnelPage selectPage(PersonnelQueryDto query);

    PersonnelEntity selectById(Long id);

    PersonnelEntity insert(PersonnelCreateCommand input);

    PersonnelEntity updateById(Long id, PersonnelUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

