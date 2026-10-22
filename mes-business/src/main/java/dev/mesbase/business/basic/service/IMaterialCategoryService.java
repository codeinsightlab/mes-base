package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.domain.MaterialCategoryPage;
import com.github.pagehelper.Page;
import dev.mesbase.business.basic.domain.MaterialCategoryCreateCommand;
import dev.mesbase.business.basic.domain.MaterialCategoryEntity;
import dev.mesbase.business.basic.domain.MaterialCategoryQueryDto;
import dev.mesbase.business.basic.domain.MaterialCategoryUpdateCommand;
import dev.mesbase.business.basic.sync.ExternalMaterialCategory;
import dev.mesbase.business.basic.sync.MaterialCategorySyncResult;
import dev.mesbase.core.factory.FactoryScope;
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

