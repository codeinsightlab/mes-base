package dev.issuefixture;

import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real administrative issue HTTP and generated Mapper/XML on isolated H2.
 */
class RoutingProductReferenceHttpTest {
    @Test
    void renamedMaterialStillMatchesItsRoutingAndCodeOnlyInputResolvesCurrentIdentity() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var db = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            var http = MaterialIssueHttpTest.mvc(context);
            db.update("UPDATE material SET code='RENAMED-M1' WHERE id=1");
            http.perform(full(post(MaterialIssueHttpTest.H)).content(MaterialIssueHttpTest.HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("productId").value("1"))
                    .andExpect(jsonPath("productCode").value("RENAMED-M1"));
            http.perform(full(post(MaterialIssueHttpTest.H)).content("""
                            {"issueNo":"CODE-ONLY","productCode":"RENAMED-M1","routingId":"1","status":"DRAFT"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("productId").value("1"));
        }
    }

    @Test
    void unrelatedProductAndUnresolvableProductCodeCannotBypassApplicability() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var db = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            var http = MaterialIssueHttpTest.mvc(context);
            db.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(2,'M2','另一产品',1,'ENABLED')");
            http.perform(full(post(MaterialIssueHttpTest.H)).content("""
                            {"issueNo":"WRONG-ID","productId":"2","routingId":"1","status":"DRAFT"}
                            """))
                    .andExpect(status().isBadRequest());
            http.perform(full(post(MaterialIssueHttpTest.H)).content("""
                            {"issueNo":"MISSING-CODE","productCode":"MISSING","routingId":"1","status":"DRAFT"}
                            """))
                    .andExpect(status().isBadRequest());
        }
    }
}
