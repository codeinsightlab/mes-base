package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.WorkOrderProgress.Completion;
import dev.mesbase.business.production.domain.WorkOrderProgress.Filter;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import dev.mesbase.business.production.domain.WorkOrderProgress.Quality;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Cross-table workorder display and bounded completion-fact aggregation.
 */
public interface WorkOrderProgressExtraMapper {
    List<Order> orders(@Param("filter") Filter filter);

    List<Completion> completions(@Param("ids") List<Long> ids);

    List<Quality> quality(@Param("ids") List<Long> ids);
}
