package dev.mesbase.business.basic.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.basic.domain.MaterialCreateCommand;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialQueryDto;
import dev.mesbase.business.basic.domain.MaterialUpdateCommand;
import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.basic.sync.MaterialSyncResult;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * 物料产品 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMaterialService {
    Page<MaterialEntity> selectPage(MaterialQueryDto query);

    MaterialEntity selectById(Long id);

    MaterialEntity insert(MaterialCreateCommand input);

    MaterialEntity updateById(Long id, MaterialUpdateCommand input);

    void deleteByIds(List<Long> ids);
    MaterialSyncResult sync(FactoryScope scope);

    MaterialSyncResult upsert(FactoryScope scope,
            List<ExternalMaterial> inputs);
}

