package dev.issuefixture;

import com.github.pagehelper.PageInterceptor;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.MesOperationUserCreateCommand;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.business.production.domain.MesExecutionAssignmentCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import dev.mesbase.business.production.domain.MesExecutionAssignmentEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ReportOperatorEligibilityService;
import dev.mesbase.business.production.service.impl.MesOperationUserServiceImpl;
import dev.mesbase.business.production.service.impl.MesExecutionAssignmentServiceImpl;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.production.OperationUserDirectoryMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE designated SIMPLE_B / real center authorization; all business writes explicitly rolled back.
 */
@EnabledIfEnvironmentVariable(named = "MES_OPERATOR_MYSQL_ACCEPTANCE", matches = "true")
class OperatorAssignmentMySqlTest {
    @Test
    void liveQualificationAndGeneratedSqlRollback() throws Exception {
        var source = AcceptanceBusinessDatabase.source();
        var config = new Configuration(new Environment("LIVE SIMPLE_B ROLLBACK ONLY", new JdbcTransactionFactory(), source));
        for (var mapper : MaterialIssueHttpTest.MAPPERS) config.addMapper(mapper);
        for (var mapper : List.of(OperationMapper.class, MesOperationUserMapper.class, MesExecutionAssignmentMapper.class))
            if (!config.hasMapper(mapper)) config.addMapper(mapper);
        var sessions = new SqlSessionFactoryBuilder().build(config);
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        var platform = (Map<?, ?>) ((Map<?, ?>) root.get("mes")).get("platform");
        var centerSource = new DriverManagerDataSource((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password"));
        var center = new PlatformPersistence(centerSource, Set.of(SysMenuMapper.class, OperationUserDirectoryMapper.class), c -> {
            c.getTypeAliasRegistry().registerAlias("SysMenu", SysMenu.class);
            c.addInterceptor(new PageInterceptor());
        });
        var directory = new OperationUserDirectory(center, center.mapper(SysMenuMapper.class), center.mapper(OperationUserDirectoryMapper.class));
        String tag = "TEST_OPERATOR_" + UUID.randomUUID();
        FactoryContext.bind(new FactoryScope(new FactoryId("SIMPLE_B")));
        try (var session = sessions.openSession(false)) {
            assertEquals("mes_base_dev_business_simple_b", session.getConnection().getCatalog());
            var candidates = directory.page(null, 0, 2);
            assertTrue(!candidates.items()
                    .isEmpty(), "Need a current authorized system user in designated acceptance factory");
            long user = candidates.items().get(0).id();
            Long other = candidates.items().size() > 1 ? candidates.items().get(1).id() : null;
            directory.requireValid(user);
            assertThrows(FoundationException.class, () -> directory.requireValid(Long.MAX_VALUE));
            var actor = new SysUser();
            actor.setUserId(user);
            actor.setUserName("TEST_ONLY");
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(user, 1L, actor, Set.of()), null, List.of()));
            long executionId, nodeId, operationId;
            try (var q = session.getConnection()
                    .createStatement(); var rs = q.executeQuery("SELECT e.id,n.id,n.operation_id FROM mes_production_execution e JOIN routing_node n ON n.routing_id=e.routing_id JOIN operation o ON o.id=n.operation_id WHERE e.status IN ('READY','RUNNING') AND o.status='ENABLED' ORDER BY e.id,n.id LIMIT 1")) {
                assertTrue(rs.next(), "Needs existing active execution and routing node");
                executionId = rs.getLong(1);
                nodeId = rs.getLong(2);
                operationId = rs.getLong(3);
            }
            var caps = session.getMapper(MesOperationUserMapper.class);
            var assignments = session.getMapper(MesExecutionAssignmentMapper.class);
            var executions = session.getMapper(MesProductionExecutionMapper.class);
            var locks = new ProductionChainLocks(session.getMapper(WorkorderMapper.class), executions, session.getMapper(MesMaterialIssueMapper.class));
            var rules = new OperatorQualificationRules(session.getMapper(OperationMapper.class), session.getMapper(RoutingNodeMapper.class), executions, caps, directory, locks);
            var capability = new MesOperationUserServiceImpl(caps, rules);
            var assignment = new MesExecutionAssignmentServiceImpl(assignments, rules);
            var eligibility = new ReportOperatorEligibilityService(rules, assignments);
            // Work only on a copied synthetic execution; existing domain records remain untouched.
            var original = executions.selectByPrimaryKey(executionId);
            var synthetic = new MesProductionExecutionEntity();
            synthetic.setWorkorderId(original.getWorkorderId());
            synthetic.setProductId(original.getProductId());
            synthetic.setRoutingId(original.getRoutingId());
            synthetic.setTargetQty(original.getTargetQty());
            synthetic.setStatus("READY");
            synthetic.setRemark(tag);
            assertEquals(1, executions.insert(synthetic));
            executionId = synthetic.getId();
            // Existing users may already be qualified. Isolate the whole definition instead of deleting their capability.
            var opMapper = session.getMapper(OperationMapper.class);
            var testOperation = opMapper.selectByPrimaryKey(operationId);
            testOperation.setId(null);
            testOperation.setCode(tag);
            testOperation.setName("TEST ONLY qualification");
            opMapper.insert(testOperation);
            var routingMapper = session.getMapper(RoutingMapper.class);
            var testRouting = routingMapper.selectByPrimaryKey(original.getRoutingId());
            testRouting.setId(null);
            testRouting.setCode(tag);
            testRouting.setName("TEST ONLY qualification");
            testRouting.setVersion(1);
            routingMapper.insert(testRouting);
            var nodeMapper = session.getMapper(RoutingNodeMapper.class);
            var testNode = nodeMapper.selectByPrimaryKey(nodeId);
            testNode.setId(null);
            testNode.setRoutingId(testRouting.getId());
            testNode.setOperationId(testOperation.getId());
            testNode.setOperationCode(tag);
            testNode.setOperationName("TEST ONLY qualification");
            nodeMapper.insert(testNode);
            synthetic.setRoutingId(testRouting.getId());
            executions.updateByPrimaryKey(synthetic);
            nodeId = testNode.getId();
            operationId = testOperation.getId();
            final long eid = executionId, nid = nodeId, oid = operationId;
            var pre = new MesOperationUserExample();
            pre.createCriteria().andOperationIdEqualTo(oid).andUserIdEqualTo(user);
            assertEquals(0, caps.countByExample(pre), "Refuse collision with existing operator configuration");
            assertEquals(ErrorCode.OPERATOR_CAPABILITY_REQUIRED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(eid, nid, user)).code());
            var c = capability.insert(new MesOperationUserCreateCommand(oid, user, tag));
            if (other != null) capability.insert(new MesOperationUserCreateCommand(oid, other, tag));
            assertNotNull(c.getCreateBy());
            eligibility.requireEligible(eid, nid, user);
            assertThrows(RuntimeException.class, () -> capability.insert(new MesOperationUserCreateCommand(oid, user, tag)));
            var a = assignment.insert(new MesExecutionAssignmentCreateCommand(eid, nid, user, tag));
            assertEquals(user, a.getAssignedBy());
            assertThrows(RuntimeException.class, () -> assignment.insert(new MesExecutionAssignmentCreateCommand(eid, nid, user, tag)));
            eligibility.requireEligible(eid, nid, user);
            if (other != null)
                assertEquals(ErrorCode.OPERATOR_NOT_ASSIGNED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(eid, nid, other)).code());
            capability.deleteByIds(List.of(c.getId()));
            assertEquals(ErrorCode.OPERATOR_CAPABILITY_REQUIRED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(eid, nid, user)).code());
            var own = new MesExecutionAssignmentExample();
            own.createCriteria().andExecutionIdEqualTo(eid);
            assertEquals(1, assignments.countByExample(own));
            assignment.deleteByIds(List.of(a.getId()));
            capability.insert(new MesOperationUserCreateCommand(oid, user, tag));
            eligibility.requireEligible(eid, nid, user);
            session.rollback();
        } finally {
            FactoryContext.clear();
            SecurityContextHolder.clearContext();
        }
        try (var session = sessions.openSession()) {
            var f = new MesOperationUserExample();
            f.createCriteria().andRemarkEqualTo(tag);
            assertEquals(0, session.getMapper(MesOperationUserMapper.class).countByExample(f));
            var a = new MesExecutionAssignmentExample();
            a.createCriteria().andRemarkEqualTo(tag);
            assertEquals(0, session.getMapper(MesExecutionAssignmentMapper.class).countByExample(a));
            var e = new MesProductionExecutionExample();
            e.createCriteria().andRemarkEqualTo(tag);
            assertEquals(0, session.getMapper(MesProductionExecutionMapper.class).countByExample(e));
        }
    }
}
