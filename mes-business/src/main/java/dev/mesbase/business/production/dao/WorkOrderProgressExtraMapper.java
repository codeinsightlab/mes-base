package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.WorkOrderProgress.Completion;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table workorder display and bounded completion-fact aggregation.
 */
public interface WorkOrderProgressExtraMapper {
    List<Order> orders(@Param("pattern") String pattern, @Param("status") String status);

    List<Completion> completions(@Param("ids") List<Long> ids);
}
