package dev.issuefixture;

import java.math.BigDecimal;
import dev.mesbase.business.wm.service.StockTransferService;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import dev.mesbase.business.wm.controller.StockTransferController;
import dev.mesbase.business.wm.controller.WmInventoryController;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H2 transaction/HTTP proof only. MySQL and actual UI acceptance are separate.
 */
class StockTransferHttpTest {
    @Configuration
    @Import({StockTransferService.class, StockTransferController.class, WmInventoryController.class, WmInventoryServiceImpl.class})
    static class TransferConfig {
    }

    static final String ROOT = "/api/wm/stock-transfers";
    static final ObjectMapper JSON = new ObjectMapper();

    static String save(String request, String quantity) {
        return "{\"requestId\":\"" + request + "\",\"reason\":\"TEST ONLY transfer\",\"items\":[{\"sourceStockId\":\"1\",\"targetStockId\":\"3\",\"quantity\":\"" + quantity + "\"}]}";
    }

    static JsonNode create(MockMvc mvc, String body) throws Exception {
        return JSON.readTree(mvc.perform(full(post(ROOT)).content(body)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    static void seed(JdbcTemplate db) {
        db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(3,1,1,0,0,'ENABLED','L001','BIN2')");
    }

    static String id(JsonNode pair) {
        return pair.get("outbound").get("document").get("id").asText();
    }

    static String postBody(JsonNode pair, String request) {
        return "{\"requestId\":\"" + request + "\",\"expectedRevision\":\"" + pair.get("outbound").get("document")
                .get("revision").asText() + "\"}";
    }

    static void balance(JdbcTemplate db, String id, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=?", BigDecimal.class, id)));
    }

    static String automaticTarget(String request, String bin) {
        return "{\"requestId\":\"" + request + "\",\"reason\":\"TEST ONLY auto destination\",\"items\":[{\"sourceStockId\":\"1\",\"targetWarehouseId\":\"2\",\"targetBinCode\":\"" + bin + "\",\"quantity\":\"5\"}]}";
    }

    @Test
    void destinationIdentityIsCreatedReusedAndRolledBackWithTheDraft() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            db.update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(2,'W2','TEST ONLY target warehouse','ENABLED')");
            var first = create(mvc, automaticTarget(UUID.randomUUID().toString(), "NEW"));
            String target = first.get("inbound").get("lines").get(0).get("stockId").asText();
            balance(db, target, "0");
            assertEquals("L001", first.get("inbound").get("lines").get(0).get("batchNo").asText());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            var second = create(mvc, automaticTarget(UUID.randomUUID().toString(), "NEW"));
            assertEquals(target, second.get("inbound").get("lines").get(0).get("stockId").asText());
            String invalid = automaticTarget(UUID.randomUUID().toString(), "ROLLBACK")
                    .replace("}]}", "},{\"sourceStockId\":\"1\",\"targetWarehouseId\":\"1\",\"targetBinCode\":\"BIN1\",\"quantity\":\"1\"}]}");
            mvc.perform(full(post(ROOT)).content(invalid)).andExpect(status().isConflict());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE warehouse_id=2 AND bin_code='ROLLBACK'", Integer.class));
            mvc.perform(full(post(ROOT + "/" + id(first) + "/actions/approve"))
                    .content(postBody(first, UUID.randomUUID().toString()))).andExpect(status().isOk());
            balance(db, "1", "9995");
            balance(db, target, "5");
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    static class DraftInsertionGate implements Interceptor {
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public Object intercept(Invocation call) throws Throwable {
            var statement = (MappedStatement) call.getArgs()[0];
            if (statement.getId()
                    .equals("dev.mesbase.business.wm.dao.WmStockDocumentLineMapper.insert") && first.compareAndSet(true, false)) {
                ready.countDown();
                if (!proceed.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("TEST ONLY line insertion gate timed out");
            }
            return call.proceed();
        }
    }

    @Test
    void savingDraftProtectsZeroBalanceDestinationAgainstConcurrentDeletion() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class);
             var pool = Executors.newFixedThreadPool(2)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            seed(db);
            var gate = new DraftInsertionGate();
            c.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(gate);
            var creating = pool.submit(() -> create(mvc, save(UUID.randomUUID().toString(), "1")));
            assertTrue(gate.ready.await(5, TimeUnit.SECONDS));
            var deleting = pool.submit(() -> mvc.perform(full(delete("/api/wm/wm-inventory")).content("[3]"))
                    .andReturn().getResponse().getStatus());
            try {
                assertThrows(TimeoutException.class, () -> deleting.get(200, TimeUnit.MILLISECONDS));
            } finally {
                gate.proceed.countDown();
            }
            creating.get(10, TimeUnit.SECONDS);
            assertEquals(409, deleting.get(10, TimeUnit.SECONDS));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE id=3", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line WHERE stock_id=3", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void disabledDestinationCannotCreateIdentityOrApproveAfterItWasDisabled() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            db.update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(2,'W2','TEST ONLY target warehouse','DISABLED')");
            mvc.perform(full(post(ROOT)).content(automaticTarget(UUID.randomUUID().toString(), "DISABLED")))
                    .andExpect(status().isUnprocessableEntity());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE warehouse_id=2", Integer.class));
            db.update("UPDATE wm_warehouse SET status='ENABLED' WHERE id=2");
            var pair = create(mvc, automaticTarget(UUID.randomUUID().toString(), "DISABLED_LATER"));
            db.update("UPDATE wm_warehouse SET status='DISABLED' WHERE id=2");
            mvc.perform(full(post(ROOT + "/" + id(pair) + "/actions/approve"))
                    .content(postBody(pair, UUID.randomUUID().toString()))).andExpect(status().isUnprocessableEntity());
            balance(db, "1", "10000");
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void concurrentDraftsResolveOneFormalDestinationIdentity() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class);
             var pool = Executors.newFixedThreadPool(2)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            db.update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(2,'W2','TEST ONLY target warehouse','ENABLED')");
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> {
                start.await();
                return create(mvc, automaticTarget(UUID.randomUUID().toString(), "CONCURRENT"));
            });
            var second = pool.submit(() -> {
                start.await();
                return create(mvc, automaticTarget(UUID.randomUUID().toString(), "CONCURRENT"));
            });
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.get("inbound").get("lines").get(0).get("stockId"), b.get("inbound").get("lines").get(0)
                    .get("stockId"));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE warehouse_id=2 AND bin_code='CONCURRENT'", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            balance(db, "1", "10000");
        }
    }

    @Test
    void productionOnlyCommandsStayOutboundAndUnknownTypeRemainsReadable() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            var input = JSON.readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String id = input.get("documentId").asText();
            String in = "/api/wm/inbound-documents/" + id;
            String out = "/api/wm/outbound-documents/" + id;
            mvc.perform(full(get(in + "/production-input-basis"))).andExpect(status().isNotFound());
            mvc.perform(full(post(in + "/actions/void"))).andExpect(status().isNotFound());
            mvc.perform(full(post(in + "/actions/reconcile-void"))).andExpect(status().isNotFound());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            mvc.perform(full(get(out + "/production-input-basis"))).andExpect(status().isOk());
            mvc.perform(full(post(out + "/actions/void"))).andExpect(status().isOk());
            mvc.perform(full(post(out + "/actions/reconcile-void"))).andExpect(status().isOk());
            var raw = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null);
            String unknown = raw.get("document").get("id").asText();
            db.update("UPDATE wm_stock_document SET document_type='MATERIAL_ISSUE' WHERE id=?", unknown);
            mvc.perform(full(get("/api/wm/inbound-documents/" + unknown))).andExpect(status().isOk());
            mvc.perform(full(post("/api/wm/inbound-documents/" + unknown + "/actions/void")))
                    .andExpect(status().isNotFound());
            mvc.perform(full(post("/api/wm/inbound-documents/" + unknown + "/actions/reconcile-void")))
                    .andExpect(status().isNotFound());
            mvc.perform(full(get("/api/wm/inbound-documents/" + unknown + "/production-input-basis")))
                    .andExpect(status().isNotFound());
            db.update("UPDATE wm_stock_document SET document_type='UNRECOGNIZED' WHERE id=?", unknown);
            var read = JSON.readTree(mvc.perform(full(get("/api/wm/inbound-documents/" + unknown)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("UNRECOGNIZED", read.get("document").get("documentType").asText());
            mvc.perform(full(put("/api/wm/inbound-documents/" + unknown))
                            .content("{\"expectedRevision\":0,\"reason\":\"must stay blocked\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void transferReplayEditCancelAndDirectionGuards() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            seed(db);
            String request = UUID.randomUUID().toString(), body = save(request, "10");
            var pair = create(mvc, body);
            assertEquals(id(pair), id(create(mvc, body)));
            String inboundId = pair.get("inbound").get("document").get("id").asText();
            var peerLookup = JSON.readTree(mvc.perform(full(get(ROOT + "/" + inboundId))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals(id(pair), id(peerLookup));
            mvc.perform(full(post(ROOT + "/" + inboundId + "/actions/approve"))
                    .content(postBody(pair, UUID.randomUUID().toString()))).andExpect(status().isNotFound());
            var list = JSON.readTree(mvc.perform(full(get(ROOT))).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString());
            assertEquals(1, list.get("total").asInt());
            assertEquals("TRANSFER_ISSUE", list.get("items").get(0).get("documentType").asText());
            mvc.perform(full(post(ROOT)).content(save(request, "11"))).andExpect(status().isConflict());
            var edited = JSON.readTree(mvc.perform(full(put(ROOT + "/" + id(pair))).content(save(request, "12").replace("\"reason\"", "\"expectedRevision\":\"" + pair.get("outbound")
                            .get("document").get("revision").asText() + "\",\"reason\""))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            mvc.perform(full(get("/api/wm/inbound-documents/" + id(pair)))).andExpect(status().isNotFound());
            mvc.perform(full(post("/api/wm/outbound-documents/" + id(pair) + "/actions/approve")).content(postBody(edited, UUID.randomUUID()
                    .toString()))).andExpect(status().isUnprocessableEntity());
            String approve = postBody(edited, UUID.randomUUID().toString());
            mvc.perform(full(post(ROOT + "/" + id(pair) + "/actions/approve")).content(approve))
                    .andExpect(status().isOk());
            mvc.perform(full(post(ROOT + "/" + id(pair) + "/actions/approve")).content(approve))
                    .andExpect(status().isOk());
            balance(db, "1", "9988");
            balance(db, "3", "12");
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            var draft = create(mvc, save(UUID.randomUUID().toString(), "1"));
            mvc.perform(full(post(ROOT + "/" + id(draft) + "/actions/cancel")).content("{\"expectedRevision\":\"" + draft.get("outbound")
                    .get("document").get("revision").asText() + "\"}")).andExpect(status().isOk());
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE status='CANCELLED'", Integer.class));
            balance(db, "1", "9988");
            balance(db, "3", "12");
            mvc.perform(full(get("/api/wm/inbound-documents")).param("direction", "OUT"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void insufficientAndSecondSideFailureRollbackEverything() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            seed(db);
            var pair = create(mvc, save(UUID.randomUUID().toString(), "10001"));
            mvc.perform(full(post(ROOT + "/" + id(pair) + "/actions/approve")).content(postBody(pair, UUID.randomUUID()
                    .toString()))).andExpect(status().isConflict());
            db.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT fail_target CHECK(stock_id<>3)");
            var second = create(mvc, save(UUID.randomUUID().toString(), "5"));
            mvc.perform(full(post(ROOT + "/" + id(second) + "/actions/approve")).content(postBody(second, UUID.randomUUID()
                    .toString()))).andExpect(status().is5xxServerError());
            balance(db, "1", "10000");
            balance(db, "3", "0");
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE status='POSTED'", Integer.class));
        }
    }

    @Test
    void hundredLineDraftCanBeEditedAndLargeLineNumbersCompareByValue() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            seed(db);
            String request = UUID.randomUUID().toString();
            String item = "{\"sourceStockId\":\"1\",\"targetStockId\":\"3\",\"quantity\":\"1\"}";
            String body = "{\"requestId\":\"" + request + "\",\"reason\":\"TEST ONLY 100 lines\",\"items\":["
                    + String.join(",", Collections.nCopies(100, item)) + "]}";
            var pair = create(mvc, body);
            String updated = body.replace("\"reason\"", "\"expectedRevision\":\"" + pair.get("outbound").get("document")
                    .get("revision").asText() + "\",\"reason\"");
            var edited = JSON.readTree(mvc.perform(full(put(ROOT + "/" + id(pair))).content(updated))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            db.update("UPDATE wm_stock_document_line SET line_no=line_no+128");
            mvc.perform(full(post(ROOT + "/" + id(pair) + "/actions/approve"))
                    .content(postBody(edited, UUID.randomUUID().toString()))).andExpect(status().isOk());
            balance(db, "1", "9900");
            balance(db, "3", "100");
            assertEquals(200, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void concurrentTransfersHaveOneWinner() throws Exception {
        try (var c = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, TransferConfig.class); var pool = Executors.newFixedThreadPool(2)) {
            var db = ProductionInputHttpTest.db(c);
            var mvc = MaterialIssueHttpTest.mvc(c);
            seed(db);
            var a = create(mvc, save(UUID.randomUUID().toString(), "6000"));
            var b = create(mvc, save(UUID.randomUUID().toString(), "6000"));
            var start = new CountDownLatch(1);
            var x = pool.submit(() -> {
                start.await();
                return mvc.perform(full(post(ROOT + "/" + id(a) + "/actions/approve")).content(postBody(a, UUID.randomUUID()
                        .toString()))).andReturn().getResponse().getStatus();
            });
            var y = pool.submit(() -> {
                start.await();
                return mvc.perform(full(post(ROOT + "/" + id(b) + "/actions/approve")).content(postBody(b, UUID.randomUUID()
                        .toString()))).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(609, x.get(20, TimeUnit.SECONDS) + y.get(20, TimeUnit.SECONDS));
            balance(db, "1", "4000");
            balance(db, "3", "6000");
        }
    }
}
