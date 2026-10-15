package dev.issuefixture;

import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.WorkorderErpFacts;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY routed SQL/API proof: an incomplete row must not erase an otherwise readable page.
 */
class WorkOrderListResilienceHttpTest {
    @Test
    void retainsIncompleteAndHealthyRowsWithAccuratePaginationAndStrictDetail() throws Exception {
        try (var context = ProductionReportHttpTest.context()) {
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(2,'TEST_MISSING_EXTRA',1,10,1,'DRAFT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder"), "A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items.length()").value(2))
                    .andExpect(jsonPath("items[0].workorder.id").value("2"))
                    .andExpect(jsonPath("items[0].erpExtraMissing").value(true))
                    .andExpect(jsonPath("items[1].workorder.id").value("1"))
                    .andExpect(jsonPath("items[1].erpExtraMissing").value(false));
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder?offset=1&limit=1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items.length()").value(1))
                    .andExpect(jsonPath("items[0].workorder.id").value("1"));
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder/2"), "A"))
                    .andExpect(status().isConflict());
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder/1"), "A"))
                    .andExpect(status().isOk());
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder"), "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
                    .andExpect(jsonPath("items[0].erpExtraMissing").value(false));
        }
    }

    @Test
    void defaultDescendingIdsApplyBeforePaginationAndAfterFilters() throws Exception {
        try (var context = ProductionReportHttpTest.context()) {
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            for (long id : List.of(10L, 12L, 11L)) {
                jdbc.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(?, ?,1,10,1,'DRAFT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "TEST_SORT_" + id);
                jdbc.update("INSERT INTO workorder_erp_extra(id) VALUES(?)", id);
            }
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder?code=TEST_SORT&status=DRAFT&limit=2"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(3))
                    .andExpect(jsonPath("items[0].workorder.id").value("12"))
                    .andExpect(jsonPath("items[1].workorder.id").value("11"));
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder?code=TEST_SORT&status=DRAFT&offset=2&limit=2"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(3))
                    .andExpect(jsonPath("items.length()").value(1))
                    .andExpect(jsonPath("items[0].workorder.id").value("10"));
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/workorder?code=TEST_SORT&status=RELEASED"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
        }
    }

    @Test
    void databaseFailureStillPropagatesInsteadOfReturningFalseSuccess() {
        var mapper = mock(WorkorderErpExtraMapper.class);
        when(mapper.selectByExample(any())).thenThrow(new DataAccessResourceFailureException("TEST ONLY"));
        var row = new WorkorderEntity();
        row.setId(1L);
        assertThrows(DataAccessResourceFailureException.class,
                () -> new WorkorderErpFacts(mapper).hydrateAvailable(List.of(row)));
    }
}
