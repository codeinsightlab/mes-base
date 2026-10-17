package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleasePage;
import dev.mesbase.business.quality.domain.MesQualityReleaseQueryDto;

/**
 * 正式质量放行事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesQualityReleaseService {
    MesQualityReleasePage selectPage(MesQualityReleaseQueryDto query);

    MesQualityReleaseEntity selectById(String id);


}

