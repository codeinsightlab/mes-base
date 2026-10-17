package dev.mesbase.server.integration.e10;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.wm.service.IWmWarehouseService;

/**
 * SDK 按工厂中心配置惰性装配；启动、保存配置均不访问 ERP。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "true")
public class DigiwinE10Configuration {
    @Bean(destroyMethod = "close")
    DigiwinE10MoQueries digiwinE10MoQueries(FactoryOapiService configurations) {
        return new DigiwinE10MoQueries(configurations);
    }

    @Bean
    DigiwinE10SyncPreview digiwinE10SyncPreview(DigiwinE10MoQueries queries, SyncCoordinator coordinator) {
        return new DigiwinE10SyncPreview(queries, coordinator);
    }

    @Bean
    @ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
    DigiwinE10SyncMapping digiwinE10SyncMapping(MaterialMapper materials, UnitMeasureMapper units,
                                                RoutingMapper routings, RoutingProductMapper products,
                                                WorkorderMapper orders, WmWarehouseMapper warehouses,
                                                WorkOrderService workorders, IWmWarehouseService warehouseService) {
        return new DigiwinE10SyncMapping(materials, units, routings, products, orders, warehouses, workorders, warehouseService);
    }

    @Bean
    @ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
    DigiwinE10SyncWriter digiwinE10SyncWriter(DigiwinE10SyncMapping mapping, WorkOrderService workorders,
                                              IWmWarehouseService warehouses) {
        return new DigiwinE10SyncWriter(mapping, workorders, warehouses);
    }

    @Bean
    @ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
    DigiwinE10SyncService digiwinE10SyncService(DigiwinE10MoQueries queries, FactoryOapiService configurations,
                                                DigiwinE10SyncMapping mapping, DigiwinE10SyncWriter writer,
                                                SyncCoordinator coordinator) {
        return new DigiwinE10SyncService(queries, configurations, mapping, writer, coordinator);
    }
}
