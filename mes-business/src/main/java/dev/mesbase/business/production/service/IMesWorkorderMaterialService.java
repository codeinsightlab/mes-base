package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesWorkorderMaterialPage;
import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialQueryDto;

/**
 * 本地工单用料需求 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesWorkorderMaterialService {
    MesWorkorderMaterialPage selectPage(MesWorkorderMaterialQueryDto query);

    MesWorkorderMaterialEntity selectById(Long id);


}

