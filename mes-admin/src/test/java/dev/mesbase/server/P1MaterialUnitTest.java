package dev.mesbase.server;

import java.util.concurrent.ExecutionException;

import dev.mesbase.business.basic.sync.MaterialQueryPort;

import dev.mesbase.business.production.service.RoutingReferenceRules;

import org.springframework.beans.factory.support.StaticListableBeanFactory;

import org.apache.ibatis.session.Configuration;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.MaterialCategoryMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.dao.WorkshopMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.basic.dao.SafetyStockMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialCategoryEntity;
import dev.mesbase.business.basic.domain.MaterialUpdateCommand;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.SafetyStockEntity;
import dev.mesbase.business.basic.service.BasicDataRules;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.business.basic.service.MaterialUnitRules;
import dev.mesbase.business.basic.service.EquipmentReferenceRules;
import dev.mesbase.business.basic.service.impl.MaterialServiceImpl;
import dev.mesbase.business.basic.service.IMaterialService;
import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.RoutingProductEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * MOCK/H2 or opt-in MYSQL, actual production identity guard, reference locks and generated SQL.
 */
class P1MaterialUnitTest {
    static final class Harness implements AutoCloseable {
        final P1TestDatabase db = new P1TestDatabase();
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(db.source);
        final SqlSessionTemplate sessions;
        final MaterialUnitRules units;
        final MaterialServiceImpl service;
        final StockReferenceRules references;

        Harness() throws Exception {
            db.table("material", MaterialEntity.class, "id");
            db.table("material_category", MaterialCategoryEntity.class, "id");
            db.table("unit_measure", UnitMeasureEntity.class, "id");
            db.table("safety_stock", SafetyStockEntity.class, "id");
            db.table("wm_inventory", WmInventoryEntity.class, "id");
            db.table("wm_stock_action", WmStockActionEntity.class, "id");
            db.table("wm_stock_document_line", WmStockDocumentLineEntity.class, "id");
            db.table("mes_workorder_material", MesWorkorderMaterialEntity.class, "id");
            db.table("routing_product", RoutingProductEntity.class, "id");
            db.table("workorder", WorkorderEntity.class, "id");
            db.table("mes_production_execution", MesProductionExecutionEntity.class, "id");
            var config = new Configuration();
            for (var mapper : Set.of(MaterialMapper.class, UnitMeasureMapper.class, MaterialCategoryMapper.class,
                    WorkshopMapper.class, WorkstationMapper.class, SafetyStockMapper.class, WmInventoryMapper.class,
                    WmStockActionMapper.class, WmStockDocumentLineMapper.class, MesWorkorderMaterialMapper.class,
                    RoutingProductMapper.class, WorkorderMapper.class, MesProductionExecutionMapper.class))
                config.addMapper(mapper);
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(db.source);
            factory.setConfiguration(config);
            sessions = new SqlSessionTemplate(factory.getObject());
            var empty = new StaticListableBeanFactory();
            units = new MaterialUnitRules(mapper(MaterialMapper.class), mapper(UnitMeasureMapper.class), mapper(WmInventoryMapper.class),
                    mapper(WmStockActionMapper.class), mapper(WmStockDocumentLineMapper.class), mapper(MesWorkorderMaterialMapper.class),
                    mapper(RoutingProductMapper.class), mapper(WorkorderMapper.class), mapper(MesProductionExecutionMapper.class), empty.getBeanProvider(MaterialBomService.class));
            var rules = new BasicDataRules(mapper(MaterialMapper.class), mapper(MaterialCategoryMapper.class), mapper(UnitMeasureMapper.class),
                    mapper(WorkstationMapper.class), mapper(WorkshopMapper.class), mapper(SafetyStockMapper.class), empty.getBeanProvider(RoutingReferenceRules.class),
                    mock(EquipmentReferenceRules.class), empty.getBeanProvider(StockReferenceRules.class), units, empty.getBeanProvider(MaterialBomService.class));
            service = P1CorrectnessTest.proxy(new MaterialServiceImpl(mapper(MaterialMapper.class), rules,
                    empty.getBeanProvider(MaterialQueryPort.class), mock(SyncCoordinator.class),
                    empty.getBeanProvider(IMaterialService.class), Validation.buildDefaultValidatorFactory()
                    .getValidator(),
                    mock(BusinessCacheProcessor.class)), manager);
            references = new StockReferenceRules(mapper(MaterialMapper.class), mock(WmWarehouseMapper.class), mapper(WmInventoryMapper.class),
                    mapper(WmStockActionMapper.class), mapper(MesWorkorderMaterialMapper.class), mapper(WmStockDocumentLineMapper.class));
            db.jdbc.update("INSERT INTO unit_measure(id,code,name,status) VALUES(1,'TEST_KG','kg','ENABLED'),(2,'TEST_PCS','pcs','ENABLED')");
            db.jdbc.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(1,'TEST_M','TEST material',1,'ENABLED')");
        }

        <T> T mapper(Class<T> type) {
            return sessions.getMapper(type);
        }

        TransactionTemplate transaction() {
            var tx = new TransactionTemplate(manager);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return tx;
        }

        public void close() {
            db.close();
        }
    }

    static MaterialUpdateCommand change(Long unit) {
        return new MaterialUpdateCommand("TEST_M", "TEST renamed", null, null, unit, "ENABLED", null, null);
    }

    @Test
    void unusedCanChangeButEveryHistoricalReferenceFreezesUnitAndNonMeteringEditsRemainAllowed() throws Exception {
        try (var h = new Harness()) {
            h.service.updateById(1L, change(2L));
            h.service.updateById(1L, change(1L));
            var inserts = List.of(
                    "INSERT INTO wm_inventory(id,material_id,quantity) VALUES(1,1,0)",
                    "INSERT INTO safety_stock(id,material_id,min_qty) VALUES(1,1,0)",
                    "INSERT INTO wm_stock_action(id,material_id,unit_id) VALUES(1,1,1)",
                    "INSERT INTO wm_stock_document_line(id,material_id,unit_id) VALUES(1,1,1)",
                    "INSERT INTO mes_workorder_material(id,material_id,unit_id) VALUES(1,1,1)",
                    "INSERT INTO routing_product(id,material_id) VALUES(1,1)",
                    "INSERT INTO workorder(id,product_id) VALUES(1,1)",
                    "INSERT INTO mes_production_execution(id,product_id) VALUES(1,1)");
            for (String insert : inserts) {
                h.transaction().executeWithoutResult(status -> {
                    h.db.jdbc.update(insert);
                    assertThrows(RuntimeException.class, () -> h.service.updateById(1L, change(2L)));
                    status.setRollbackOnly();
                });
                assertEquals(1L, h.db.jdbc.queryForObject("SELECT unit_id FROM material WHERE id=1", Long.class));
            }
            h.db.jdbc.update("INSERT INTO wm_inventory(id,material_id,quantity) VALUES(1,1,0)");
            assertDoesNotThrow(() -> h.service.updateById(1L, change(1L)));
            var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
            assertThrows(RuntimeException.class, () -> FactoryContext.runWithScope(scope, () -> h.service.upsert(scope,
                    List.of(new ExternalMaterial("TEST_M", "TEST", null, null, 2L, "ENABLED", null, null)))));
            assertEquals(1L, h.db.jdbc.queryForObject("SELECT unit_id FROM material WHERE id=1", Long.class));
            assertEquals(0, h.db.jdbc.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", Integer.class));
        }
    }

    @Test
    void directHistoricalUnitReferencePreventsDeletionEvenWithoutCurrentMaterial() throws Exception {
        try (var h = new Harness()) {
            h.db.jdbc.update("INSERT INTO wm_stock_action(id,material_id,unit_id) VALUES(1,999,2)");
            assertThrows(RuntimeException.class, () -> h.transaction()
                    .executeWithoutResult(status -> h.units.deletingUnits(List.of(2L))));
            assertEquals(2, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM unit_measure", Integer.class));
        }
    }

    @Test
    void quantityReferenceCreationHoldingMaterialLockMakesConcurrentUnitChangeWaitThenReject() throws Exception {
        // The real helper is shared by inventory creation and production workorder-material creation.
        for (boolean production : List.of(false, true)) {
            try (var h = new Harness(); var threads = Executors.newFixedThreadPool(2)) {
                var locked = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                var entered = new CountDownLatch(1);
                var create = threads.submit(() -> h.transaction().executeWithoutResult(status -> {
                    h.references.lockDemandReferences(1L, List.of());
                    Long unit = h.mapper(MaterialMapper.class).selectByPrimaryKey(1L).getUnitId();
                    locked.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (production) {
                        h.db.jdbc.update("INSERT INTO mes_workorder_material(id,material_id,unit_id,total_required_qty) VALUES(1,1,?,100)", unit);
                    } else {
                        h.db.jdbc.update("INSERT INTO wm_inventory(id,material_id,quantity) VALUES(1,1,100)");
                    }
                }));
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var edit = threads.submit(() -> {
                    entered.countDown();
                    return h.service.updateById(1L, change(2L));
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> edit.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                create.get(10, TimeUnit.SECONDS);
                assertThrows(ExecutionException.class, () -> edit.get(10, TimeUnit.SECONDS));
                assertEquals(1L, h.db.jdbc.queryForObject("SELECT unit_id FROM material WHERE id=1", Long.class));
                if (production) {
                    assertEquals(1L, h.db.jdbc.queryForObject("SELECT unit_id FROM mes_workorder_material WHERE id=1", Long.class));
                    assertEquals(100, h.db.jdbc.queryForObject("SELECT total_required_qty FROM mes_workorder_material WHERE id=1", Integer.class));
                } else {
                    assertEquals(100, h.db.jdbc.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", Integer.class));
                }
            }
        }
    }
}
