package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.RoutingEntity;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkOrderEnumSqlTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.addResultMap(new ResultMap.Builder(config,
                "dev.mesbase.business.production.dao.RoutingMapper.BaseResultMap", RoutingEntity.class, List.of()).build());
        String resource = "dev/mesbase/business/production/dao/WorkOrderExtraMapper.xml";
        try (var input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test
    void publishedProductQueryBindsOriginalCodes() throws Exception {
        var sql = configuration().getMappedStatement(
                        "dev.mesbase.business.production.dao.WorkOrderExtraMapper.publishedForProduct")
                .getBoundSql(Map.of("productId", 1L, "search", "test"));
        assertEquals("PRODUCT", sql.getAdditionalParameter("productType"));
        assertEquals("ENABLED", sql.getAdditionalParameter("enabledStatus"));
        assertEquals("PUBLISHED", sql.getAdditionalParameter("publishedStatus"));
        assertFalse(sql.getSql().contains("'PRODUCT'"));
        assertEquals(6, sql.getParameterMappings().size());
    }

    @Test
    void blockingIssueQueryStillRequiresCompleteVoidProof() throws Exception {
        BoundSql sql = configuration().getMappedStatement(
                        "dev.mesbase.business.production.dao.WorkOrderExtraMapper.countBlockingIssues")
                .getBoundSql(Map.of("workorderId", 1L));
        // Isolated H2 fixture verifies real generated SQL and parameters; it is not live MySQL evidence.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:enum_void;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE mes_material_issue(id BIGINT,workorder_id BIGINT,status VARCHAR(30))");
            statement.execute("CREATE TABLE mes_material_issue_erp_extra(id BIGINT,void_result_status VARCHAR(30),source_execution_mode VARCHAR(30),void_execution_mode VARCHAR(30),external_effect VARCHAR(30))");
            statement.execute("INSERT INTO mes_material_issue VALUES (1,1,'DRAFT'),(2,1,'VOIDED'),(3,1,'VOIDED'),(4,1,'VOIDED'),(5,1,'VOIDED'),(6,2,'DRAFT')");
            statement.execute("INSERT INTO mes_material_issue_erp_extra VALUES (2,'SUCCEEDED','LOCAL_ONLY','LOCAL_ONLY','NONE'),(3,'SUCCEEDED','CONNECTED','CONNECTED','REVERSED'),(4,'UNKNOWN','CONNECTED','CONNECTED','UNKNOWN'),(5,'SUCCEEDED','LOCAL_ONLY','CONNECTED','NONE')");
            try (var query = connection.prepareStatement(sql.getSql())) {
                for (int index = 0; index < sql.getParameterMappings().size(); index++) {
                    String name = sql.getParameterMappings().get(index).getProperty();
                    query.setObject(index + 1, sql.hasAdditionalParameter(name) ? sql.getAdditionalParameter(name) : 1L);
                }
                try (var result = query.executeQuery()) {
                    result.next();
                    assertEquals(3, result.getLong(1));
                }
            }
        }
    }
}
