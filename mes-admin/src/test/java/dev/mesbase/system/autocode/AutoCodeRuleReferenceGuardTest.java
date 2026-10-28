package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mesbase.business.autocode.dao.AutoCodeDocumentReferenceMapper;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessPersistence;
import dev.mesbase.infrastructure.persistence.OwnedSqlSession;
import dev.mesbase.server.SystemAutoCodeRuleReferenceGuard;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AutoCodeRuleReferenceGuardTest {
    @Test
    void firstPrefixChecksOnlyMatchingNumbersNotTheWholeDocumentCategory() {
        var factories = mock(ManagedDatasourceService.class);
        var business = mock(BusinessPersistence.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var session = mock(OwnedSqlSession.class);
        var documents = mock(AutoCodeDocumentReferenceMapper.class);
        var factory = new ManagedDatasourceService.FactoryView("A", "E", "A", "工厂A", 8L, "ENABLED", 1,
                "业务库A", "LOCAL", null, null, 1, false);
        when(factories.factoryList(0, 100)).thenReturn(List.of(factory));
        when(parts.selectSysAutoCodePartList(any(SysAutoCodePart.class))).thenReturn(List.of());
        when(session.mapper(AutoCodeDocumentReferenceMapper.class)).thenReturn(documents);
        when(documents.hasStockDocumentPrefix("IN%", "STOCK_IN")).thenReturn(0);
        when(business.transaction(any(FactoryScope.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<OwnedSqlSession, Boolean> work = invocation.getArgument(1);
            return work.apply(session);
        });
        var guard = new SystemAutoCodeRuleReferenceGuard(factories, business, parts);
        var rule = new SysAutoCodeRule();
        rule.setRuleId(9L);
        rule.setRuleType("STOCK_IN");

        guard.requirePrefixChangeUnreferenced(rule, "", "IN");
        verify(documents).hasStockDocumentPrefix("IN%", "STOCK_IN");
    }

    @Test
    void scansOnlyBoundFactoryPagesWithinTheSupportedPageSizeAndUsesTheLeadingPrefix() {
        var factories = mock(ManagedDatasourceService.class);
        var business = mock(BusinessPersistence.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var session = mock(OwnedSqlSession.class);
        var documents = mock(AutoCodeDocumentReferenceMapper.class);
        var factory = new ManagedDatasourceService.FactoryView("A", "E", "A", "工厂A", 8L, "ENABLED", 1,
                "业务库A", "LOCAL", null, null, 1, false);
        when(factories.factoryList(0, 100)).thenReturn(List.of(factory));
        when(parts.selectSysAutoCodePartList(any(SysAutoCodePart.class))).thenReturn(List.of(prefixPart()));
        when(session.mapper(AutoCodeDocumentReferenceMapper.class)).thenReturn(documents);
        when(documents.hasWorkorderPrefix("WO%")).thenReturn(0);
        when(business.transaction(any(FactoryScope.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<OwnedSqlSession, Boolean> work = invocation.getArgument(1);
            return work.apply(session);
        });
        var guard = new SystemAutoCodeRuleReferenceGuard(factories, business, parts);
        var rule = new SysAutoCodeRule();
        rule.setRuleId(7L);
        rule.setRuleType("WORK_ORDER");

        assertEquals("WO", guard.requireUnreferenced(rule));

        verify(factories).factoryList(0, 100);
        verify(documents).hasWorkorderPrefix("WO%");
    }

    @Test
    void excludesConfiguredCenterDatasourceFromBusinessReferenceScan() {
        var factories = mock(ManagedDatasourceService.class);
        var business = mock(BusinessPersistence.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var session = mock(OwnedSqlSession.class);
        var documents = mock(AutoCodeDocumentReferenceMapper.class);
        var factory = new ManagedDatasourceService.FactoryView("A", "E", "A", "业务工厂", 8L, "ENABLED", 1,
                "业务库", "LOCAL", null, null, 1, false);
        var center = new ManagedDatasourceService.FactoryView("CENTER", "E", "CENTER", "中心库配置", 9L,
                "ENABLED", 1, "中心库", "LOCAL", null, null, 1, false);
        when(factories.factoryList(0, 100)).thenReturn(List.of(factory, center));
        when(factories.platformDatasourceIds(List.of(8L, 9L))).thenReturn(Set.of(9L));
        when(parts.selectSysAutoCodePartList(any(SysAutoCodePart.class))).thenReturn(List.of(prefixPart()));
        when(session.mapper(AutoCodeDocumentReferenceMapper.class)).thenReturn(documents);
        when(documents.hasWorkorderPrefix("WO%")).thenReturn(0);
        when(business.transaction(any(FactoryScope.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<OwnedSqlSession, Boolean> work = invocation.getArgument(1);
            return work.apply(session);
        });
        var guard = new SystemAutoCodeRuleReferenceGuard(factories, business, parts);
        var rule = new SysAutoCodeRule();
        rule.setRuleId(7L);
        rule.setRuleType("WORK_ORDER");

        guard.requireUnreferenced(rule);

        verify(factories).platformDatasourceIds(List.of(8L, 9L));
        verify(business, times(1)).transaction(any(FactoryScope.class), any());
        verify(documents, times(1)).hasWorkorderPrefix("WO%");
    }

    private static SysAutoCodePart prefixPart() {
        var part = new SysAutoCodePart();
        part.setPartIndex(1);
        part.setPartType("FIXCHAR");
        part.setPartLength(2);
        part.setFixCharacter("WO");
        return part;
    }
}
