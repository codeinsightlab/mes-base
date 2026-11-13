package dev.stockfixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.wm.service.StockDocumentReversalService;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

/**
 * Opt-in real MySQL acceptance for the stock ledger write and document reversal core.
 * All fixture rows and stock changes run in one transaction which is always rolled back.
 */
@EnabledIfEnvironmentVariable(named = "MES_STOCK_LEDGER_MYSQL_ACCEPTANCE", matches = "true")
class StockLedgerMySqlAcceptanceTest {
    private static final String FACTORY = "TEST_DEV_PERF_20261019";
    private static final String DATABASE = "mes_base_dev_perf_test";
    private static final String TARGET_HOST = "192.168.18.25";
    private static final int TARGET_PORT = 3306;
    private static final long ACTOR = 100L;
    private static DriverManagerDataSource source;
    private static SqlSessionTemplate sessions;
    private static OwnedTransactionManager transactions;
    private static String scope;

    @BeforeAll
    static void verifyTargetBeforeWrites() throws Exception {
        scope = "MYSQL_STOCK_LEDGER_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        verifyManagedBinding();
        source = AcceptanceBusinessDatabase.performanceTestSource();
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            assertEquals(DATABASE, scalar(connection, "SELECT DATABASE()"));
            String hostName = scalar(connection, "SELECT @@hostname");
            String serverUuid = scalar(connection, "SELECT @@server_uuid");
            String version = scalar(connection, "SELECT VERSION()");
            assertTrue(version.startsWith("8."), "Expected the approved MySQL 8 test instance");
            assertEquals("0", scalar(connection, "SELECT @@read_only"));
            assertEquals("1", scalar(connection, "SELECT COUNT(*) FROM flyway_business_schema_history "
                    + "WHERE version='88' AND success=1"));
            assertEquals("5", scalar(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='wm_stock_document' "
                    + "AND column_name IN ('reversed_by','reversed_at','reversal_reason',"
                    + "'reversal_request_id','reversal_request_hash')"));
            assertEquals("1", scalar(connection, "SELECT COUNT(DISTINCT index_name) "
                    + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                    + "AND table_name='wm_stock_document' AND index_name='uk_stock_document_reversal_request' "
                    + "AND non_unique=0"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM information_schema.innodb_trx"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM performance_schema.data_locks "
                    + "WHERE object_schema=DATABASE() AND object_name IN ('material','unit_measure',"
                    + "'wm_warehouse','wm_inventory','wm_stock_document','wm_stock_document_line','wm_stock_action')"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM performance_schema.metadata_locks "
                    + "WHERE object_schema=DATABASE() AND object_name IN ('material','unit_measure',"
                    + "'wm_warehouse','wm_inventory','wm_stock_document','wm_stock_document_line','wm_stock_action') "
                    + "AND lock_status='PENDING'"));
            System.out.println("MYSQL_ACCEPTANCE_TARGET factory=" + FACTORY + " database=" + DATABASE
                    + " host=" + TARGET_HOST + ":" + TARGET_PORT + " server=" + hostName
                    + " server_uuid=" + serverUuid + " version=" + version + " flyway=V88:Success");
        }
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        var resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(resolver.getResources("classpath*:dev/mesbase/business/**/*Mapper.xml"));
        factoryBean.afterPropertiesSet();
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        sessions = new SqlSessionTemplate(factory);
        transactions = new OwnedTransactionManager(source);
    }

    @AfterAll
    static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inboundAndOutboundLedgerFieldsAndReversalArePersistedThenRolledBack() {
        var login = new LoginUser(ACTOR, null, null, Set.of());
        var authentication = new UsernamePasswordAuthenticationToken(login, "acceptance", Set.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var unitMapper = sessions.getMapper(UnitMeasureMapper.class);
        var materialMapper = sessions.getMapper(MaterialMapper.class);
        var warehouseMapper = sessions.getMapper(WmWarehouseMapper.class);
        var inventoryMapper = sessions.getMapper(WmInventoryMapper.class);
        var actionMapper = sessions.getMapper(WmStockActionMapper.class);
        var lineMapper = sessions.getMapper(WmStockDocumentLineMapper.class);
        var documentMapper = sessions.getMapper(WmStockDocumentMapper.class);

        var sources = spy(new BusinessSourceRules(null, null, null, warehouseMapper,
                null, null, null, null));
        doNothing().when(sources).warehouse(anyString(), anyLong());
        var quantityRules = new MaterialQuantityRules(materialMapper, unitMapper, null);
        var localStock = new LocalStockService(inventoryMapper, actionMapper, materialMapper, unitMapper,
                warehouseMapper, lineMapper, documentMapper, null, sources, quantityRules);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var reversal = new StockDocumentReversalService(documentMapper, localStock, validator);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try {
                var unit = new UnitMeasureEntity();
                unit.setCode(scope + "_UNIT");
                unit.setName("MySQL 回滚验收单位");
                unit.setStatus("ENABLED");
                unit.setMustBeWholeNumber(0);
                unit.setQuantityPrecision(6);
                assertEquals(1, unitMapper.insertSelective(unit));

                var material = new MaterialEntity();
                material.setCode(scope + "_MATERIAL");
                material.setName("MySQL 回滚验收物料");
                material.setUnitId(unit.getId());
                material.setStatus("ENABLED");
                assertEquals(1, materialMapper.insertSelective(material));

                var warehouse = new dev.mesbase.business.wm.domain.WmWarehouseEntity();
                warehouse.setCode(scope + "_WAREHOUSE");
                warehouse.setName("MySQL 回滚验收仓库");
                warehouse.setStatus("ENABLED");
                warehouse.setSource("LOCAL");
                assertEquals(1, warehouseMapper.insertSelective(warehouse));

                runDirection("IN", "OTHER_RECEIPT", new BigDecimal("5.250000"),
                        new BigDecimal("100.000000"), new BigDecimal("70.000000"), material, unit,
                        warehouse, localStock, reversal, inventoryMapper, actionMapper, lineMapper, documentMapper);
                runDirection("OUT", "OTHER_ISSUE", new BigDecimal("7.125000"),
                        new BigDecimal("100.000000"), new BigDecimal("70.000000"), material, unit,
                        warehouse, localStock, reversal, inventoryMapper, actionMapper, lineMapper, documentMapper);
            } finally {
                status.setRollbackOnly();
            }
        });

        try (var connection = source.getConnection()) {
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM unit_measure WHERE code='" + scope + "_UNIT'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM material WHERE code='" + scope + "_MATERIAL'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM wm_warehouse WHERE code='" + scope + "_WAREHOUSE'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM wm_stock_document WHERE document_no LIKE '" + scope + "_%'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM wm_stock_document_line l "
                    + "JOIN wm_stock_document d ON d.id=l.document_id WHERE d.document_no LIKE '" + scope + "_%'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM wm_stock_action WHERE reason LIKE '" + scope + "_%'"));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM wm_inventory i JOIN material m ON m.id=i.material_id "
                    + "WHERE m.code='" + scope + "_MATERIAL'"));
        } catch (Exception failure) {
            throw new AssertionError("Unable to verify MySQL acceptance rollback", failure);
        }
        System.out.println("MYSQL_ACCEPTANCE_RESULT inbound=PASS outbound=PASS ledger_identity=PASS "
                + "reversal_audit=PASS replay_idempotency=PASS rollback=PASS scope=" + scope);
    }

    private static void runDirection(String direction, String type, BigDecimal quantity,
                                     BigDecimal openingQuantity, BigDecimal openingAvailable,
                                     MaterialEntity material, UnitMeasureEntity unit,
                                     dev.mesbase.business.wm.domain.WmWarehouseEntity warehouse,
                                     LocalStockService localStock, StockDocumentReversalService reversal,
                                     WmInventoryMapper inventories, WmStockActionMapper actions,
                                     WmStockDocumentLineMapper lines, WmStockDocumentMapper documents) {
        String side = direction.equals("IN") ? "INBOUND" : "OUTBOUND";
        String batch = scope + "_BATCH_" + side;
        String bin = "BIN_" + side;
        var stock = new WmInventoryEntity();
        stock.setMaterialId(material.getId());
        stock.setWarehouseId(warehouse.getId());
        stock.setQuantity(openingQuantity);
        stock.setAvailableQuantity(openingAvailable);
        stock.setStatus("ENABLED");
        stock.setBatchNo(batch);
        stock.setBinCode(bin);
        assertEquals(1, inventories.insertSelective(stock));

        var document = new WmStockDocumentEntity();
        document.setDocumentNo(scope + "_" + side);
        document.setDocumentType(type);
        document.setDirection(direction);
        document.setStatus("DRAFT");
        document.setReason(scope + "_" + side + "_POST_REASON");
        document.setCreateBy(Long.toString(ACTOR));
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateBy(Long.toString(ACTOR));
        document.setUpdateTime(LocalDateTime.now());
        document.setRevision(1L);
        assertEquals(1, documents.insertSelective(document));

        var line = new WmStockDocumentLineEntity();
        line.setDocumentId(document.getId());
        line.setLineNo(1);
        line.setMaterialId(material.getId());
        line.setWarehouseId(warehouse.getId());
        line.setStockId(stock.getId());
        line.setBatchNo(batch);
        line.setBinCode(bin);
        line.setUnitId(unit.getId());
        line.setQuantity(quantity);
        line.setRevision(1L);
        assertEquals(1, lines.insertSelective(line));

        String postRequest = UUID.randomUUID().toString();
        String postHash = "a".repeat(64);
        var original = localStock.postDocument(direction, type, document.getId(), line, postRequest,
                postRequest, postHash, document.getReason(), null);
        assertNotNull(original.getId());
        line.setStockActionId(original.getId());
        assertEquals(1, lines.updateByPrimaryKeySelective(line));
        document.setStatus("POSTED");
        document.setPostRequestId(postRequest);
        document.setPostRequestHash(postHash);
        document.setPostedBy(ACTOR);
        document.setPostedAt(LocalDateTime.now());
        document.setRevision(2L);
        document.setUpdateTime(LocalDateTime.now());
        assertEquals(1, documents.updateByPrimaryKeyWithBLOBs(document));

        BigDecimal postedDelta = direction.equals("IN") ? quantity : quantity.negate();
        var postedStock = inventories.selectByPrimaryKey(stock.getId());
        assertEquals(openingQuantity.add(postedDelta), postedStock.getQuantity());
        assertEquals(openingAvailable.add(postedDelta), postedStock.getAvailableQuantity());
        var persistedOriginal = actions.selectByPrimaryKey(original.getId());
        assertNotNull(persistedOriginal);
        assertEquals(postHash, persistedOriginal.getRequestHash());
        assertEquals(postRequest, persistedOriginal.getOperationId());
        assertEquals(line.getId(), persistedOriginal.getDocumentLineId());
        assertEquals(stock.getId(), persistedOriginal.getStockId());
        assertEquals(document.getId().toString(), persistedOriginal.getSourceId());
        assertEquals(type, persistedOriginal.getSourceType());
        assertEquals(document.getReason(), persistedOriginal.getReason());
        assertEquals(postedDelta, persistedOriginal.getQuantityDelta());
        assertEquals(postedDelta, persistedOriginal.getAvailableDelta());
        assertEquals(openingQuantity, persistedOriginal.getQuantityBefore());
        assertEquals(openingQuantity.add(postedDelta), persistedOriginal.getQuantityAfter());
        assertEquals(openingAvailable, persistedOriginal.getAvailableBefore());
        assertEquals(openingAvailable.add(postedDelta), persistedOriginal.getAvailableAfter());
        assertEquals(material.getId(), persistedOriginal.getMaterialId());
        assertEquals(warehouse.getId(), persistedOriginal.getWarehouseId());
        assertEquals(unit.getId(), persistedOriginal.getUnitId());
        assertEquals(batch, persistedOriginal.getBatchNo());
        assertEquals(bin, persistedOriginal.getBinCode());
        assertEquals(ACTOR, persistedOriginal.getOperatorUserId());
        assertNotNull(persistedOriginal.getOccurredAt());
        assertEquals(persistedOriginal.getOccurredAt(), persistedOriginal.getRecordedAt());

        String reversalRequest = UUID.randomUUID().toString();
        String reason = scope + "_" + side + "_REVERSAL_REASON";
        var cancelled = reversal.reverse(document.getId(),
                new StockDocumentModels.Cancellation(2L, reversalRequest, reason));
        assertEquals("REVERSED", cancelled.getStatus());
        assertEquals(3L, cancelled.getRevision());
        assertEquals(ACTOR, cancelled.getReversedBy());
        assertNotNull(cancelled.getReversedAt());
        assertEquals(reason, cancelled.getReversalReason());
        assertEquals(reversalRequest, cancelled.getReversalRequestId());
        assertNotNull(cancelled.getReversalRequestHash());
        assertEquals(postedDelta, persistedOriginal.getQuantityDelta());
        assertEquals(original.getId(), lines.selectByPrimaryKey(line.getId()).getStockActionId());

        var reversalFilter = new WmStockActionExample();
        reversalFilter.createCriteria().andReferenceActionIdEqualTo(original.getId());
        var reversalRows = actions.selectByExample(reversalFilter);
        assertEquals(1, reversalRows.size());
        var reversed = reversalRows.getFirst();
        assertEquals("REVERSAL", reversed.getActionType());
        assertEquals(postedDelta.negate(), reversed.getQuantityDelta());
        assertEquals(postedDelta.negate(), reversed.getAvailableDelta());
        assertEquals(openingQuantity.add(postedDelta), reversed.getQuantityBefore());
        assertEquals(openingQuantity, reversed.getQuantityAfter());
        assertEquals(openingAvailable.add(postedDelta), reversed.getAvailableBefore());
        assertEquals(openingAvailable, reversed.getAvailableAfter());
        assertEquals(original.getId(), reversed.getReferenceActionId());
        assertEquals(line.getId(), reversed.getDocumentLineId());
        assertEquals(reversalRequest, reversed.getOperationId());
        assertEquals(persistedOriginal.getSourceType(), reversed.getSourceType());
        assertEquals(persistedOriginal.getSourceId(), reversed.getSourceId());
        assertEquals(persistedOriginal.getStockId(), reversed.getStockId());
        assertEquals(persistedOriginal.getMaterialId(), reversed.getMaterialId());
        assertEquals(persistedOriginal.getWarehouseId(), reversed.getWarehouseId());
        assertEquals(persistedOriginal.getUnitId(), reversed.getUnitId());
        assertEquals(persistedOriginal.getBatchNo(), reversed.getBatchNo());
        assertEquals(persistedOriginal.getBinCode(), reversed.getBinCode());
        var finalStock = inventories.selectByPrimaryKey(stock.getId());
        assertEquals(openingQuantity, finalStock.getQuantity());
        assertEquals(openingAvailable, finalStock.getAvailableQuantity());

        var replay = reversal.reverse(document.getId(),
                new StockDocumentModels.Cancellation(2L, reversalRequest, reason));
        assertEquals("REVERSED", replay.getStatus());
        assertEquals(1, actions.selectByExample(reversalFilter).size());
        assertFalse(actions.selectByPrimaryKey(original.getId()).getId().isBlank());
        assertTrue(reversed.getOccurredAt() != null && reversed.getRecordedAt() != null);
        assertThrows(RuntimeException.class, () -> reversal.reverse(document.getId(),
                new StockDocumentModels.Cancellation(3L, UUID.randomUUID().toString(), "different reason")));
    }

    private static void verifyManagedBinding() throws Exception {
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> mes = (Map<?, ?>) root.get("mes");
        Map<?, ?> platform = (Map<?, ?>) mes.get("platform");
        try (var center = DriverManager.getConnection((String) platform.get("url"),
                (String) platform.get("username"), (String) platform.get("password"));
             var query = center.prepareStatement("SELECT f.factory_id,d.datasource_id,d.revision,d.db_host,d.db_port,"
                     + "d.db_name,f.runtime_mode,f.status,d.status FROM mf_factory f JOIN mf_datasource d "
                     + "ON d.datasource_id=f.datasource_id WHERE f.factory_id=?")) {
            query.setString(1, FACTORY);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next(), "Expected an active managed test Factory binding");
                assertEquals(FACTORY, rows.getString(1));
                assertEquals(9L, rows.getLong(2));
                assertTrue(rows.getLong(3) > 0);
                assertEquals(TARGET_HOST, rows.getString(4));
                assertEquals(TARGET_PORT, rows.getInt(5));
                assertEquals(DATABASE, rows.getString(6));
                assertEquals("LOCAL", rows.getString(7));
                assertEquals("ENABLED", rows.getString(8));
                assertEquals("ENABLED", rows.getString(9));
                assertFalse(rows.next(), "Factory binding must be unique");
            }
        }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "Query returned no scalar value");
            String value = rows.getString(1);
            assertFalse(rows.next(), "Query returned more than one scalar value");
            return value;
        }
    }

}
