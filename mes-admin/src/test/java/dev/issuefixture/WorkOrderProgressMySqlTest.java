package dev.issuefixture;

import com.github.pagehelper.PageHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import dev.mesbase.business.production.domain.WorkOrderProgress.Filter;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: resolve the designated SIMPLE_B binding, SELECT only.
 */
@EnabledIfEnvironmentVariable(named = "MES_WORKSPACE_MYSQL_ACCEPTANCE", matches = "true")
class WorkOrderProgressMySqlTest {
    @Test
    void explainsGeneratedCodePrefixQueriesOnTheDesignatedDatabase() throws Exception {
        var configuration = new Configuration(new Environment("SIMPLE_B READ ONLY", new JdbcTransactionFactory(), AcceptanceBusinessDatabase.source()));
        configuration.addMapper(WorkOrderProgressExtraMapper.class);
        PersistenceFactories.pagination(configuration);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            session.getConnection().setReadOnly(true);
            String workorderCode;
            String productCode;
            try (var select = session.getConnection()
                    .prepareStatement("SELECT w.code,m.code FROM workorder w JOIN material m ON m.id=w.product_id ORDER BY w.id DESC LIMIT 1");
                 var row = select.executeQuery()) {
                assertTrue(row.next(), "指定库需要已有工单才能验证检索执行计划");
                workorderCode = row.getString(1);
                productCode = row.getString(2);
            }
            var mapped = configuration.getMappedStatement(WorkOrderProgressExtraMapper.class.getName() + ".orders");
            int index = 0;
            for (var filter : List.of(new Filter(null, null, "ALL"),
                    new Filter(escapePrefix(workorderCode), null, "ALL"),
                    new Filter(null, escapePrefix(productCode), "ALL"),
                    new Filter(escapePrefix(workorderCode), escapePrefix(productCode), "ALL"))) {
                var parameters = Map.of("filter", filter);
                var bound = mapped.getBoundSql(parameters);
                try (var explain = session.getConnection()
                        .prepareStatement("EXPLAIN FORMAT=JSON " + bound.getSql() + " LIMIT 5")) {
                    for (int i = 0; i < bound.getParameterMappings().size(); i++) {
                        String property = bound.getParameterMappings().get(i).getProperty();
                        Object value = bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property)
                                : SystemMetaObject.forObject(parameters).getValue(property);
                        explain.setObject(i + 1, value);
                    }
                    try (var plan = explain.executeQuery()) {
                        assertTrue(plan.next());
                        JsonNode json = new ObjectMapper().readTree(plan.getString(1));
                        var summaries = new ArrayList<String>();
                        summarizePlan(json, summaries);
                        // 仅输出访问方式和索引，不输出实际编码、条件或完整SQL。
                        System.out.println("SIMPLE_B progress plan case=" + index++ + " " + summaries);
                    }
                }
                try (var page = PageHelper.offsetPage(0, 5)) {
                    var matches = session.getMapper(WorkOrderProgressExtraMapper.class).orders(filter);
                    if (filter.workorderCodePattern() != null)
                        assertTrue(matches.stream().allMatch(order -> order.code().startsWith(workorderCode)));
                    if (filter.productCodePattern() != null)
                        assertTrue(matches.stream().allMatch(order -> order.productCode().startsWith(productCode)));
                    assertTrue(!matches.isEmpty());
                }
            }
            session.rollback();
        }
    }

    private static String escapePrefix(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static void summarizePlan(JsonNode node, List<String> result) {
        if (node.isObject() && node.has("table_name"))
            result.add(node.path("table_name").asText() + ":" + node.path("access_type").asText()
                    + ":" + node.path("key").asText() + ":rows=" + node.path("rows_examined_per_scan").asText());
        node.forEach(child -> summarizePlan(child, result));
    }

    @Test
    void pagesTheConfiguredOrdersAndAggregatesOnlyTheirFormalCompletionFacts() throws Exception {
        var configuration = new Configuration(new Environment("SIMPLE_B READ ONLY", new JdbcTransactionFactory(), AcceptanceBusinessDatabase.source()));
        configuration.addMapper(WorkOrderProgressExtraMapper.class);
        PersistenceFactories.pagination(configuration);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            session.getConnection().setReadOnly(true);
            assertEquals("mes_base_dev_business_simple_b", session.getConnection().getCatalog());
            var mapper = session.getMapper(WorkOrderProgressExtraMapper.class);
            try (var page = PageHelper.offsetPage(0, 5)) {
                var orders = mapper.orders(new Filter(null, null, null));
                assertTrue(orders.size() <= 5);
                long total = page.getTotal();
                if (!orders.isEmpty()) {
                    var ids = orders.stream().map(Order::id).toList();
                    var totals = mapper.completions(ids);
                    assertTrue(totals.stream().allMatch(t -> ids.contains(t.workorderId())));
                    var quality = mapper.quality(ids);
                    assertTrue(quality.stream().allMatch(t -> ids.contains(t.workorderId())));
                    assertTrue(quality.stream().allMatch(t -> t.declaredQty() != null && t.scrapQty() != null
                            && t.failedQty() != null && t.recoveredQty() != null));
                    System.out.println("SIMPLE_B workorder progress: total=" + total + ", page=" + orders.size() + ", completedOrders=" + totals.size());
                }
            }
            try (var statement = session.getConnection().prepareStatement(
                    "SELECT w.id, w.code, SUM(c.effective_qty) AS quantity FROM workorder w JOIN mes_production_execution e ON e.workorder_id=w.id JOIN mes_execution_completion c ON c.execution_id=e.id GROUP BY w.id,w.code ORDER BY w.id DESC LIMIT 1");
                 var completed = statement.executeQuery()) {
                if (completed.next()) {
                    long id = completed.getLong("id");
                    var expected = completed.getBigDecimal("quantity");
                    var totals = mapper.completions(List.of(id));
                    assertEquals(1, totals.size());
                    assertEquals(0, expected.compareTo(totals.getFirst().quantity()));
                    System.out.println("SIMPLE_B completed workorder verified: id=" + id + ", quantity=" + expected.toPlainString());
                } else {
                    System.out.println("SIMPLE_B has no formal completion facts; no sample facts were written.");
                }
            }
            var qualityOrderIds = new ArrayList<Long>();
            try (var statement = session.getConnection().prepareStatement(
                    "SELECT DISTINCT e.workorder_id FROM mes_production_execution e JOIN mes_production_defect d ON d.execution_id=e.id ORDER BY e.workorder_id DESC LIMIT 5");
                 var result = statement.executeQuery()) {
                while (result.next()) qualityOrderIds.add(result.getLong(1));
            }
            if (!qualityOrderIds.isEmpty()) {
                var quality = mapper.quality(qualityOrderIds);
                assertTrue(quality.stream().allMatch(row -> qualityOrderIds.contains(row.workorderId())));
                assertTrue(quality.stream()
                        .allMatch(row -> row.declaredQty() != null && row.destinationType() != null));
                System.out.println("SIMPLE_B current quality declarations read=" + quality.size());
            }
            session.rollback();
        }
    }
}
