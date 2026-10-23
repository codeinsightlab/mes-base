package dev.mesbase.business.production;

import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesOperationUserEntity;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.business.production.domain.MesExecutionAssignmentEntity;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import dev.mesbase.business.production.domain.MesOperationUserCreateCommand;
import dev.mesbase.business.production.domain.MesOperationUserUpdateCommand;
import dev.mesbase.business.production.domain.MesExecutionAssignmentCreateCommand;
import dev.mesbase.business.production.domain.MesExecutionAssignmentUpdateCommand;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.business.production.service.ReportOperatorEligibilityService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.impl.MesOperationUserServiceImpl;
import dev.mesbase.business.production.service.impl.MesExecutionAssignmentServiceImpl;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Unit / mock evidence, deliberately independent of DB and HTTP authorization.
 */
class OperatorQualificationTest {
    OperationMapper operations = mock(OperationMapper.class);
    RoutingNodeMapper nodes = mock(RoutingNodeMapper.class);
    MesProductionExecutionMapper executions = mock(MesProductionExecutionMapper.class);
    MesOperationUserMapper capabilities = mock(MesOperationUserMapper.class);
    MesExecutionAssignmentMapper assignments = mock(MesExecutionAssignmentMapper.class);
    OperationUserDirectory users = mock(OperationUserDirectory.class);
    ProductionChainLocks locks = mock(ProductionChainLocks.class);
    OperatorQualificationRules rules = new OperatorQualificationRules(operations, nodes, executions, capabilities, users, locks);
    ReportOperatorEligibilityService eligibility = new ReportOperatorEligibilityService(rules, assignments);
    MesProductionExecutionEntity execution;

    @BeforeEach
    void prepare() {
        FactoryContext.bind(new FactoryScope(new FactoryId("TEST_A")));
        var operation = new OperationEntity();
        operation.setId(10L);
        operation.setStatus("ENABLED");
        when(operations.selectByPrimaryKey(10L)).thenReturn(operation);
        var node = new RoutingNodeEntity();
        node.setId(20L);
        node.setRoutingId(30L);
        node.setOperationId(10L);
        var repeated = new RoutingNodeEntity();
        repeated.setId(21L);
        repeated.setRoutingId(30L);
        repeated.setOperationId(10L);
        when(nodes.selectByPrimaryKey(20L)).thenReturn(node);
        when(nodes.selectByPrimaryKey(21L)).thenReturn(repeated);
        execution = new MesProductionExecutionEntity();
        execution.setId(40L);
        execution.setRoutingId(30L);
        execution.setStatus("READY");
        when(executions.selectByPrimaryKey(40L)).thenReturn(execution);
        when(locks.execution(40L)).thenReturn(execution);
        var user = new SysUser();
        user.setUserId(99L);
        user.setUserName("TEST_ONLY");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(99L, 1L, user, Set.of()), null, List.of()));
    }

    @AfterEach
    void clear() {
        FactoryContext.clear();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"false,false,false,false", "true,false,false,true", "false,true,true,false", "true,true,false,false", "true,true,true,true"})
    void completeEligibilityMatrix(boolean capable, boolean hasAssignments, boolean assigned, boolean allowed) {
        when(capabilities.countByExample(any(MesOperationUserExample.class))).thenReturn(capable ? 1L : 0L);
        when(assignments.countByExample(any(MesExecutionAssignmentExample.class))).thenReturn(hasAssignments ? 1L : 0L, assigned ? 1L : 0L);
        if (allowed) assertDoesNotThrow(() -> eligibility.requireEligible(40L, 20L, 1L));
        else assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 20L, 1L));
    }

    @Test
    void cancellingCapabilityAndLastAssignmentTakesEffectOnNextCall() {
        var capable = new AtomicBoolean(true);
        var assigned = new AtomicBoolean(true);
        when(capabilities.countByExample(any())).thenAnswer(i -> capable.get() ? 1L : 0L);
        when(assignments.countByExample(any())).thenAnswer(i -> assigned.get() ? 1L : 0L);
        eligibility.requireEligible(40L, 20L, 1L);
        capable.set(false);
        assertEquals(ErrorCode.OPERATOR_CAPABILITY_REQUIRED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 20L, 1L)).code());
        verify(assignments, never()).deleteByExample(any());
        capable.set(true);
        assigned.set(false);
        eligibility.requireEligible(40L, 20L, 1L);
    }

    @Test
    void repeatedOperationNodesRemainIndependent() {
        when(capabilities.countByExample(any())).thenReturn(1L);
        when(assignments.countByExample(any())).thenAnswer(i -> {
            MesExecutionAssignmentExample e = i.getArgument(0);
            var cs = e.getOredCriteria().getFirst().getAllCriteria();
            var node = cs.stream().filter(c -> c.getCondition().equals("routing_node_id =")).findFirst().orElseThrow()
                    .getValue();
            return node.equals(20L) ? 1L : cs.size() == 2 ? 1L : 0L;
        });
        eligibility.requireEligible(40L, 20L, 1L);
        assertEquals(ErrorCode.OPERATOR_NOT_ASSIGNED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 21L, 1L)).code());
    }

    @Test
    void missingFactoryFailsClosed() {
        FactoryContext.clear();
        assertEquals(ErrorCode.FACTORY_SCOPE_REQUIRED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 20L, 1L)).code());
        verify(executions, never()).selectByPrimaryKey(any());
    }

    @Test
    void wrongRoutingVersionFails() {
        execution.setRoutingId(31L);
        assertEquals(ErrorCode.ASSIGNMENT_NODE_INVALID, assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 20L, 1L)).code());
    }

    @Test
    void deletedExecutionOrNodeFails() {
        assertEquals(ErrorCode.EXECUTION_NOT_FOUND, assertThrows(FoundationException.class, () -> eligibility.requireEligible(41L, 20L, 1L)).code());
        assertEquals(ErrorCode.ASSIGNMENT_NODE_INVALID, assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 22L, 1L)).code());
    }

    @Test
    void inactiveOrUnauthorizedIdentityIsRejected() {
        doThrow(new FoundationException(ErrorCode.OPERATOR_NOT_AUTHORIZED)).when(users).requireValid(1L);
        assertEquals(ErrorCode.OPERATOR_NOT_AUTHORIZED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(40L, 20L, 1L)).code());
    }

    @Test
    void failedQueryNeverAllowsReporting() {
        when(capabilities.countByExample(any())).thenThrow(new IllegalStateException("TEST_ONLY query failure"));
        assertThrows(IllegalStateException.class, () -> eligibility.requireEligible(40L, 20L, 1L));
    }

    @Test
    void capabilityRequiresExistingOperationAndValidUser() {
        var service = new MesOperationUserServiceImpl(capabilities, rules);
        assertThrows(FoundationException.class, () -> service.insert(new MesOperationUserCreateCommand(11L, 1L, null)));
        doThrow(new FoundationException(ErrorCode.OPERATOR_NOT_AUTHORIZED)).when(users).requireValid(2L);
        assertThrows(FoundationException.class, () -> service.insert(new MesOperationUserCreateCommand(10L, 2L, null)));
        verify(capabilities, never()).insert(any());
    }

    @Test
    void duplicateCapabilityIsSafeConflict() {
        when(capabilities.insert(any())).thenThrow(new DuplicateKeyException("TEST_ONLY"));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(FoundationException.class, () -> new MesOperationUserServiceImpl(capabilities, rules).insert(new MesOperationUserCreateCommand(10L, 1L, null))).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULLY_RECEIVED", "CANCELLED", "UNKNOWN"})
    void endedOrUnknownExecutionCannotBeAssigned(String status) {
        execution.setStatus(status);
        assertEquals(ErrorCode.ASSIGNMENT_EXECUTION_CLOSED, assertThrows(FoundationException.class, () -> new MesExecutionAssignmentServiceImpl(assignments, rules).insert(new MesExecutionAssignmentCreateCommand(40L, 20L, 1L, null))).code());
        verify(assignments, never()).insert(any());
    }

    @Test
    void incapableUserCannotBeAssigned() {
        assertEquals(ErrorCode.OPERATOR_CAPABILITY_REQUIRED, assertThrows(FoundationException.class, () -> new MesExecutionAssignmentServiceImpl(assignments, rules).insert(new MesExecutionAssignmentCreateCommand(40L, 20L, 1L, null))).code());
    }

    @Test
    void duplicateAssignmentIsSafeConflict() {
        when(capabilities.countByExample(any())).thenReturn(1L);
        when(assignments.insert(any())).thenThrow(new DuplicateKeyException("TEST_ONLY"));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(FoundationException.class, () -> new MesExecutionAssignmentServiceImpl(assignments, rules).insert(new MesExecutionAssignmentCreateCommand(40L, 20L, 1L, null))).code());
    }

    @Test
    void successfulAssignmentHasTrustedAudit() {
        when(capabilities.countByExample(any())).thenReturn(1L);
        when(assignments.insert(any())).thenAnswer(i -> {
            MesExecutionAssignmentEntity row = i.getArgument(0);
            assertEquals(99L, row.getAssignedBy());
            assertNotNull(row.getAssignedAt());
            row.setId(1L);
            return 1;
        });
        when(assignments.selectByPrimaryKey(1L)).thenReturn(new MesExecutionAssignmentEntity());
        new MesExecutionAssignmentServiceImpl(assignments, rules).insert(new MesExecutionAssignmentCreateCommand(40L, 20L, 1L, null));
    }

    @Test
    void associationKeysCannotBeTransferredByUpdate() {
        var row = new MesOperationUserEntity();
        row.setId(1L);
        row.setOperationId(10L);
        row.setUserId(1L);
        when(capabilities.selectByPrimaryKey(1L)).thenReturn(row);
        assertThrows(FoundationException.class, () -> new MesOperationUserServiceImpl(capabilities, rules).updateById(1L, new MesOperationUserUpdateCommand(10L, 2L, null)));
        var a = new MesExecutionAssignmentEntity();
        a.setId(2L);
        a.setExecutionId(40L);
        a.setRoutingNodeId(20L);
        a.setUserId(1L);
        when(assignments.selectByPrimaryKey(2L)).thenReturn(a);
        assertThrows(FoundationException.class, () -> new MesExecutionAssignmentServiceImpl(assignments, rules).updateById(2L, new MesExecutionAssignmentUpdateCommand(40L, 21L, 1L, null)));
    }
}
