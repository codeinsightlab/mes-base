package dev.mesbase.business.quality.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleaseQueryDto;

/**
 * 正式质量放行事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesQualityReleaseService {
    Page<MesQualityReleaseEntity> selectPage(MesQualityReleaseQueryDto query);

    MesQualityReleaseEntity selectById(String id);


}

