package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.BatchRouteNodeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Historical selected paths only. No production command may write this retired model. */
public interface BatchRouteNodeMapper {
    List<BatchRouteNodeEntity> selectHistoryByBatchId(@Param("batchId") Long batchId);
}
