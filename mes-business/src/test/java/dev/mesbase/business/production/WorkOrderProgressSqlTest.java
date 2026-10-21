package dev.mesbase.business.production;

import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.domain.WorkOrderProgress.Filter;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderProgressSqlTest {
    @Test
    void explicitFiltersGenerateOnlyTheirParameterizedPredicatesWithoutCrossTableOr() {
        var configuration = new Configuration();
        configuration.addMapper(WorkOrderProgressExtraMapper.class);
        var statement = configuration.getMappedStatement(WorkOrderProgressExtraMapper.class.getName() + ".orders");
        for (var filter : List.of(new Filter(null, null, null), new Filter("WO%", null, "ALL"),
                new Filter(null, "PRD%", "RELEASED"), new Filter("WO%", "PRD%", "ALL"))) {
            var bound = statement.getBoundSql(Map.of("filter", filter));
            String sql = bound.getSql().replaceAll("\\s+", " ").trim();
            assertFalse(sql.matches("(?is).*\\bOR\\b.*"), sql);
            assertFalse(sql.contains("m.name LIKE"));
            assertEquals(filter.workorderCodePattern() != null, sql.contains("w.code LIKE ? ESCAPE '!'"));
            assertEquals(filter.productCodePattern() != null, sql.contains("m.code LIKE ? ESCAPE '!'"));
            assertFalse(sql.contains("WO%"));
            assertFalse(sql.contains("PRD%"));
            assertTrue(sql.endsWith("ORDER BY w.id DESC"));
            long expected = (filter.workorderCodePattern() == null ? 0 : 1)
                    + (filter.productCodePattern() == null ? 0 : 1) + ("ALL".equals(filter.status()) ? 0 : 1);
            assertEquals(expected, bound.getParameterMappings().size());
        }
    }

    @Test
    void qualityQueryIsBoundedByTheSelectedPageAndMatchesTheCurrentDeclarationVersion() {
        var configuration = new Configuration();
        configuration.addMapper(WorkOrderProgressExtraMapper.class);
        var statement = configuration.getMappedStatement(WorkOrderProgressExtraMapper.class.getName() + ".quality");
        var bound = statement.getBoundSql(Map.of("ids", List.of(1L, 2L)));
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertEquals(7, bound.getParameterMappings().size());
        assertTrue(sql.contains("t.declaration_revision = d.revision_no"));
        assertTrue(sql.contains("newer.revision_no > d.revision_no"));
        assertTrue(sql.contains("d.record_action != ?"));
        assertEquals("VOID", bound.getAdditionalParameter("voidAction"));
        assertTrue(sql.contains("SUM(t.failed_qty)"));
    }
}
