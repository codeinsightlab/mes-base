// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.pro.service;

import dev.mesbase.business.pro.domain.ProWorkorderCreateCommand;
import dev.mesbase.business.pro.domain.ProWorkorderEntity;
import dev.mesbase.business.pro.domain.ProWorkorderPage;
import dev.mesbase.business.pro.domain.ProWorkorderQueryDto;
import dev.mesbase.business.pro.domain.ProWorkorderUpdateCommand;
import dev.mesbase.business.pro.sync.ExternalProWorkorder;
import dev.mesbase.business.pro.sync.ProWorkorderSyncResult;
import dev.mesbase.core.factory.FactoryScope;
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

    ProWorkorderSyncResult sync(FactoryScope scope);

    ProWorkorderSyncResult upsert(FactoryScope scope,
                                  List<ExternalProWorkorder> inputs);
}

