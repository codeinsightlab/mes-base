// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.service;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.modules.material.domain.MaterialCategoryCreateCommand;
import dev.mesbase.modules.material.domain.MaterialCategoryEntity;
import dev.mesbase.modules.material.domain.MaterialCategoryPage;
import dev.mesbase.modules.material.domain.MaterialCategoryQueryDto;
import dev.mesbase.modules.material.domain.MaterialCategoryUpdateCommand;
import dev.mesbase.modules.material.sync.ExternalMaterialCategory;
import dev.mesbase.modules.material.sync.MaterialCategorySyncResult;
import java.util.List;

/**
 * 物料产品分类 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMaterialCategoryService {
    MaterialCategoryPage selectPage(MaterialCategoryQueryDto query);

    MaterialCategoryEntity selectById(Long id);

    MaterialCategoryEntity insert(MaterialCategoryCreateCommand input);

    MaterialCategoryEntity updateById(Long id, MaterialCategoryUpdateCommand input);

    void deleteByIds(List<Long> ids);

    MaterialCategorySyncResult sync(FactoryScope scope);

    MaterialCategorySyncResult upsert(FactoryScope scope,
                                      List<ExternalMaterialCategory> inputs);
}

