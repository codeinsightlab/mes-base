package dev.stockfixture;

import dev.mesbase.business.wm.domain.WarehouseType;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import java.util.Map;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: real controller, service, SQL, transactions and authorization on two separate H2 databases.
 */
class WarehouseTypeHttpTest {
    private static final String ROOT = "/api/wm/wm-warehouse";

    private static MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder request, String factory) {
        return request.header("Authorization", "Bearer FULL").header("X-Factory-Id", factory)
                .contentType("application/json");
    }

    private static String body(String code, String type) {
        return "{\"code\":\"" + code + "\",\"name\":\"TEST ONLY warehouse\",\"status\":\"ENABLED\",\"warehouseType\":\"" + type + "\"}";
    }

    @Test
    void sevenTypesPersistFilterAndReturnLabelsAndKeepFactoriesSeparate() throws Exception {
        try (var context = StockHttpTest.context()) {
            MockMvc mvc = StockHttpTest.mvc(context);
            for (WarehouseType type : WarehouseType.values()) {
                mvc.perform(request(post(ROOT), "A").content(body(type.getValue(), type.getValue())))
                        .andExpect(status().isOk()).andExpect(jsonPath("warehouseType").value(type.getValue()))
                        .andExpect(jsonPath("warehouseTypeLabel").value(type.getDescription()));
                mvc.perform(request(get(ROOT).param("warehouseType", type.getValue()), "A"))
                        .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                        .andExpect(jsonPath("items[0].warehouseType").value(type.getValue()))
                        .andExpect(jsonPath("items[0].warehouseTypeLabel").value(type.getDescription()));
                mvc.perform(request(get(ROOT).param("warehouseType", type.getValue()), "B"))
                        .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            }
            mvc.perform(request(post(ROOT), "B").content(body("B_ONLY", "WIP"))).andExpect(status().isOk());
            mvc.perform(request(put(ROOT + "/1"), "A").content(body("RAW_MATERIAL", "SCRAP")))
                    .andExpect(status().isOk()).andExpect(jsonPath("warehouseType").value("SCRAP"));
            mvc.perform(request(get(ROOT + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("warehouseTypeLabel").value("报废品仓"));
            mvc.perform(request(get(ROOT + "/1"), "B")).andExpect(status().isOk())
                    .andExpect(jsonPath("warehouseType").value("WIP"));
            mvc.perform(request(get(ROOT).param("warehouseType", "RAW_MATERIAL"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
        }
    }

    @Test
    void rejectsUnknownMissingAndBlankTypesWithoutWriting() throws Exception {
        try (var context = StockHttpTest.context()) {
            MockMvc mvc = StockHttpTest.mvc(context);
            for (String type : new String[]{"原材料仓", "raw_material", "OTHER", "", " RAW_MATERIAL "}) {
                mvc.perform(request(post(ROOT), "A").content(body("INVALID", type)))
                        .andExpect(status().isBadRequest());
                mvc.perform(request(get(ROOT).param("warehouseType", type), "A"))
                        .andExpect(status().isBadRequest());
            }
            mvc.perform(request(post(ROOT), "A").content("{\"code\":\"MISSING\",\"name\":\"TEST\",\"status\":\"ENABLED\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(request(post(ROOT), "A").content(body("NULL", "WIP").replace("\"WIP\"", "null")))
                    .andExpect(status().isBadRequest());
            assertEquals(0, context.getBean(StockHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM wm_warehouse", Integer.class));
        }
    }

    @Test
    void legacyNullReadsExplicitlyAndTypeEditLeavesInventoryAndDocumentsUntouched() throws Exception {
        try (var context = StockHttpTest.context()) {
            MockMvc mvc = StockHttpTest.mvc(context);
            var db = context.getBean(StockHttpTest.Fixture.class).business.get("A");
            db.update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(1,'HISTORY','TEST unclassified','ENABLED')");
            db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status) VALUES(1,1,1,15,12,'ENABLED')");
            db.update("INSERT INTO wm_stock_document_line(id,document_id,line_no,material_id,warehouse_id,stock_id,unit_id,quantity,revision) VALUES(1,1,1,1,1,1,1,3,0)");
            var stockBefore = db.queryForList("SELECT * FROM wm_inventory");
            var linesBefore = db.queryForList("SELECT * FROM wm_stock_document_line");
            mvc.perform(request(get(ROOT + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("warehouseType").isEmpty())
                    .andExpect(jsonPath("warehouseTypeLabel").value("待补录"));
            mvc.perform(request(get(ROOT), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].warehouseTypeLabel").value("待补录"));
            mvc.perform(request(put(ROOT + "/1"), "A").content(body("HISTORY", "")))
                    .andExpect(status().isBadRequest());
            mvc.perform(request(put(ROOT + "/1"), "A").content(body("HISTORY", "DEFECTIVE")))
                    .andExpect(status().isOk()).andExpect(jsonPath("warehouseTypeLabel").value("不良品仓"));
            assertEquals(stockBefore, db.queryForList("SELECT * FROM wm_inventory"));
            assertEquals(linesBefore, db.queryForList("SELECT * FROM wm_stock_document_line"));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void listAndDetailExposeNamesWhileKeepingAuditIds() throws Exception {
        try (var context = StockHttpTest.context()) {
            var directory = context.getBean(BusinessDirectoryService.class);
            Mockito.when(directory.userLabels(ArgumentMatchers.anyCollection()))
                    .thenReturn(Map.of(1L, new DirectoryLabel(1L, "operator", "张三", null)));
            MockMvc mvc = StockHttpTest.mvc(context);
            mvc.perform(request(post(ROOT), "A").content(body("NAMES", "WIP"))).andExpect(status().isOk());
            mvc.perform(request(get(ROOT), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("items[0].createBy").value("1"))
                    .andExpect(jsonPath("items[0].createByName").value("张三"))
                    .andExpect(jsonPath("items[0].updateByName").value("张三"))
                    .andExpect(jsonPath("total").value(1));
            mvc.perform(request(get(ROOT + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("createByName").value("张三"))
                    .andExpect(jsonPath("updateByName").value("张三"));
        }
    }
}
