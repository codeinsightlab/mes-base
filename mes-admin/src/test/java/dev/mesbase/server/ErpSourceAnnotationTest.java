package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.ErpRoutingDataSource;
import dev.mesbase.infrastructure.persistence.ErpSource;
import dev.mesbase.infrastructure.persistence.ErpSourceAspect;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** TEST ONLY verifies the real annotation/transaction proxy order with independent H2 databases. */
class ErpSourceAnnotationTest {
    public interface ProbeMapper {
        @Select("SELECT label FROM marker") String label();
        @Update("UPDATE marker SET label = #{label}") int rename(@Param("label") String label);
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Proxies {}

    @ErpSource("hr")
    public static class HrAccess {
        private final ProbeMapper mapper;
        public HrAccess(ProbeMapper mapper) { this.mapper = mapper; }
        public String label() { return mapper.label(); }
        public String nested(WmsAccess other) { return other.label(); }
        public void fail() { mapper.rename("changed"); throw new IllegalStateException("TEST ONLY failure"); }
        @ErpSource("wms") public String overridden() { return mapper.label(); }
    }

    @ErpSource("wms")
    public static class WmsAccess {
        private final ProbeMapper mapper;
        public WmsAccess(ProbeMapper mapper) { this.mapper = mapper; }
        public String label() { return mapper.label(); }
    }

    @ErpSource("missing")
    public static class MissingAccess {
        public String label() { return "must not execute"; }
    }

    private DataSource pool(String label) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE marker(label VARCHAR(32))");
            try (var insert = connection.prepareStatement("INSERT INTO marker VALUES(?)")) {
                insert.setString(1,label); insert.executeUpdate();
            }
        }
        return source;
    }

    @Test void classAnnotationSelectsBeforeTransactionAndMethodOverrideUsesSameFactory() throws Exception {
        try (var source = new ErpRoutingDataSource(Map.of("hr", pool("HR"), "wms", pool("WMS")));
             var context = new AnnotationConfigApplicationContext()) {
            var manager = new OwnedTransactionManager(source);
            var factory = PersistenceFactories.create(source, Set.of(ProbeMapper.class), c -> {}, manager::verifyAccess);
            var mapper = new SqlSessionTemplate(factory).getMapper(ProbeMapper.class);
            context.register(Proxies.class);
            context.registerBean("erpTransactionManager",OwnedTransactionManager.class,()->manager);
            context.registerBean(ErpSourceAspect.class,ErpSourceAspect::new);
            context.registerBean(HrAccess.class,()->new HrAccess(mapper));
            context.registerBean(WmsAccess.class,()->new WmsAccess(mapper));
            context.registerBean(MissingAccess.class,MissingAccess::new);
            context.refresh();
            var hr = context.getBean(HrAccess.class);
            var wms = context.getBean(WmsAccess.class);
            assertEquals("HR",hr.label());
            assertEquals("WMS",wms.label());
            assertEquals("WMS",hr.overridden());
            assertThrows(RuntimeException.class,()->hr.nested(wms));
            assertThrows(IllegalStateException.class,hr::fail);
            assertEquals("HR",hr.label());
            assertEquals("WMS",wms.label());
            assertThrows(RuntimeException.class,()->context.getBean(MissingAccess.class).label());
            assertThrows(RuntimeException.class,mapper::label);
        }
    }
}
