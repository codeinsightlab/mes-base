package dev.issuefixture;

import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: retired ERP endpoints stay absent even for an authorized session.
 */
class MaterialIssueErpRetirementHttpTest {
    @Test
    void materialIssueErpQueryAndReconciliationAreNotExposed() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(full(get(MaterialIssueHttpTest.H + "/1/erp-status")))
                    .andExpect(status().isNotFound());
            mvc.perform(full(post(MaterialIssueHttpTest.H + "/1/actions/reconcile-erp")))
                    .andExpect(status().isNotFound());
        }
    }
}
