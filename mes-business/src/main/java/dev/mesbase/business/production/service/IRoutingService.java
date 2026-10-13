package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.RoutingCreateCommand;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingPage;
import dev.mesbase.business.production.domain.RoutingQueryDto;
import dev.mesbase.business.production.domain.RoutingUpdateCommand;
import java.util.List;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.business.production.sync.ExternalRouting;
import dev.mesbase.business.production.sync.ProductionSyncResult;
import dev.mesbase.business.production.domain.RoutingReference;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.domain.RoutingContentCommand;
import dev.mesbase.business.production.domain.RoutingCopyCommand;


/**
 * 工艺路线 CRUD 用例。
 *
 * @author MES Base
 */
public interface IRoutingService {
    RoutingPage selectPage(RoutingQueryDto query);

    RoutingEntity insert(RoutingCreateCommand input);

    RoutingEntity updateById(Long id, RoutingUpdateCommand input);

    void deleteByIds(List<Long> ids);

    ProductionSyncResult sync(FactoryScope scope);

    ProductionSyncResult upsert(FactoryScope scope, List<ExternalRouting> inputs);

    List<RoutingReference> operationOptions(String search, int limit);

    List<RoutingReference> materialOptions(String search, int limit);

    RoutingDetail detail(Long id);

    RoutingDetail saveContent(Long id, RoutingContentCommand input);

    RoutingEntity publish(Long id, Integer revision);

    RoutingEntity availability(Long id, Integer revision, boolean enabled);

    RoutingEntity newVersion(Long id, Integer revision);

    RoutingEntity copy(Long id, RoutingCopyCommand input);
}

