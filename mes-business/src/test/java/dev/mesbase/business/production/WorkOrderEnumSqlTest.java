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
        assertEquals("ENABLED", sql.getAdditionalParameter("enabledStatus"));
        assertEquals("PUBLISHED", sql.getAdditionalParameter("publishedStatus"));
        assertFalse(sql.getSql().contains("material_type"));
        assertEquals(5, sql.getParameterMappings().size());
    }

    @Test
    void allocationListExcludesCancelledExecutionsAndPreservesTheUnallocatedRemainder() throws Exception {
        var sql = configuration().getMappedStatement(
                "dev.mesbase.business.production.dao.WorkOrderExtraMapper.allocationForOrders")
                .getBoundSql(Map.of("ids", List.of(1L, 2L)));
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:active_allocations;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE workorder(id BIGINT,planned_qty DECIMAL(20,6))");
            statement.execute("CREATE TABLE mes_production_execution(id BIGINT,workorder_id BIGINT,target_qty DECIMAL(20,6),status VARCHAR(30))");
            statement.execute("INSERT INTO workorder VALUES (1,100),(2,100)");
            statement.execute("INSERT INTO mes_production_execution VALUES (1,1,60,'RUNNING'),(2,1,40,'CANCELLED'),(3,2,100,'CANCELLED')");
            try (var query = connection.prepareStatement(sql.getSql())) {
                for (int index = 0; index < sql.getParameterMappings().size(); index++) {
                    query.setObject(index + 1, sql.getAdditionalParameter(sql.getParameterMappings().get(index).getProperty()));
                }
                try (var result = query.executeQuery()) {
                    int seen = 0;
                    while (result.next()) {
                        boolean first = result.getLong("workorder_id") == 1;
                        assertEquals(first ? 60 : 0, result.getBigDecimal("allocated_target_qty").intValueExact());
                        assertEquals(first ? 40 : 100, result.getBigDecimal("remaining_target_qty").intValueExact());
                        assertEquals(first ? 1 : 0, result.getLong("execution_count"));
                        seen++;
                    }
                    assertEquals(2, seen);
                }
            }
        }
    }

    @Test
    void blockingIssueQueryRequiresACompleteLocalVoidFact() throws Exception {
        BoundSql sql = configuration().getMappedStatement(
                        "dev.mesbase.business.production.dao.WorkOrderExtraMapper.countBlockingIssues")
                .getBoundSql(Map.of("workorderId", 1L));
        // Isolated H2 fixture verifies real generated SQL and parameters; it is not live MySQL evidence.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:enum_void;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE mes_material_issue(id BIGINT,workorder_id BIGINT,status VARCHAR(30),void_result_status VARCHAR(30),source_mode VARCHAR(30))");
            statement.execute("INSERT INTO mes_material_issue VALUES (1,1,'DRAFT',NULL,NULL),(2,1,'VOIDED','SUCCEEDED','LOCAL_ONLY'),(3,1,'VOIDED','SUCCEEDED','CONNECTED'),(4,1,'VOIDED','UNKNOWN','CONNECTED'),(5,1,'VOIDED','SUCCEEDED','LOCAL_ONLY'),(6,2,'DRAFT',NULL,NULL)");
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
