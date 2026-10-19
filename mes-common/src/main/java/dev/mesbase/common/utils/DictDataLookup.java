package dev.mesbase.common.utils;

import dev.mesbase.common.core.domain.entity.SysDictData;
import java.util.List;

/**
 * Existing dictionary query exposed to common Excel consumers without a system-module dependency.
 */
@FunctionalInterface
public interface DictDataLookup {
    List<SysDictData> selectDictDataByType(String type);
}
