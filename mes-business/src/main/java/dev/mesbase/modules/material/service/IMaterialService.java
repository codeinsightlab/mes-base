// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.service;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.domain.MaterialCreateCommand;
import dev.mesbase.modules.material.domain.MaterialEntity;
import dev.mesbase.modules.material.domain.MaterialPage;
import dev.mesbase.modules.material.domain.MaterialQueryDto;
import dev.mesbase.modules.material.domain.MaterialUpdateCommand;
import dev.mesbase.modules.material.sync.ExternalMaterial;
import dev.mesbase.modules.material.sync.MaterialSyncResult;
import java.util.List;

/**
 * 物料产品 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMaterialService {
    MaterialPage selectPage(MaterialQueryDto query);

    MaterialEntity selectById(Long id);

    MaterialEntity insert(MaterialCreateCommand input);

    MaterialEntity updateById(Long id, MaterialUpdateCommand input);

    void deleteByIds(List<Long> ids);

    MaterialSyncResult sync(FactoryScope scope);

    MaterialSyncResult upsert(FactoryScope scope,
                              List<ExternalMaterial> inputs);
}

