package dev.mesbase.business.sales;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.sales.dao.MesCustomerMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderItemMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderMapper;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemCreateCommand;
import dev.mesbase.business.sales.domain.SalesOrderCreateRequest;
import dev.mesbase.business.sales.domain.SalesOrderLineRequest;
import dev.mesbase.business.sales.domain.SalesOrderUpdateRequest;
import dev.mesbase.business.sales.service.IMesSalesOrderItemService;
import dev.mesbase.business.sales.service.SalesOrderDocumentService;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderQuantityRulesTest {
    private final MesSalesOrderMapper orders = mock(MesSalesOrderMapper.class);
    private final MesSalesOrderItemMapper items = mock(MesSalesOrderItemMapper.class);
    private final MesCustomerMapper customers = mock(MesCustomerMapper.class);
    private final MaterialMapper materials = mock(MaterialMapper.class);
    private final UnitMeasureMapper units = mock(UnitMeasureMapper.class);
    private final IMesSalesOrderItemService itemService = mock(IMesSalesOrderItemService.class);
    private final MaterialQuantityRules rules = new MaterialQuantityRules(materials, units,
            mock(MesProductionExecutionMapper.class));
    private final SalesOrderDocumentService service = new SalesOrderDocumentService(
            orders, items, customers, materials, itemService, rules);
    private final UnitMeasureEntity unit = new UnitMeasureEntity();

    @BeforeEach
    void setUp() {
        MesCustomerEntity customer = new MesCustomerEntity();
        customer.setStatus("ENABLED");
        when(customers.selectByPrimaryKeyForUpdate(3L)).thenReturn(customer);
        MaterialEntity material = new MaterialEntity();
        material.setId(10L);
        material.setStatus("ENABLED");
        material.setUnitId(20L);
        when(materials.selectByPrimaryKeyForUpdate(10L)).thenReturn(material);
        unit.setId(20L);
        unit.setMustBeWholeNumber(0);
        unit.setQuantityPrecision(6);
        when(units.selectByPrimaryKeyForUpdate(20L)).thenReturn(unit);
        SysUser user = new SysUser();
        user.setUserId(99L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(99L, 1L, user, Set.of()), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"1,6,0.5", "0,0,1.1", "0,2,1.234", "0,5,1.234567"})
    void rejectsUnitRuleViolationsBeforeCreatingAnyDocument(int whole, int precision, String quantity) {
        unit.setMustBeWholeNumber(whole);
        unit.setQuantityPrecision(precision);

        FoundationException failure = assertThrows(FoundationException.class,
                () -> service.create(request(quantity)));

        assertEquals(ErrorCode.BAD_REQUEST, failure.code());
        verifyNoInteractions(orders, items, itemService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.0000001"})
    void rejectsNonpositiveAndOverSixDecimalInputsBeforeAnyLookup(String quantity) {
        FoundationException failure = assertThrows(FoundationException.class,
                () -> service.create(request(quantity)));

        assertEquals(ErrorCode.BAD_REQUEST, failure.code());
        verifyNoInteractions(orders, items, itemService, customers, materials, units);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void acceptsEachConfiguredPrecisionWithoutRoundingBeforeWriting(int precision) {
        unit.setQuantityPrecision(precision);
        String quantity = precision == 0 ? "12.000000" : "12." + "3".repeat(precision);
        MesSalesOrderEntity saved = new MesSalesOrderEntity();
        saved.setId(101L);
        when(orders.insertSelective(any())).thenAnswer(invocation -> {
            MesSalesOrderEntity header = invocation.getArgument(0);
            header.setId(101L);
            return 1;
        });
        when(orders.selectByPrimaryKey(101L)).thenReturn(saved);
        when(items.selectByExample(any())).thenReturn(List.of());

        assertEquals(101L, service.create(request(quantity)).header().getId());

        ArgumentCaptor<MesSalesOrderItemCreateCommand> line = ArgumentCaptor.forClass(MesSalesOrderItemCreateCommand.class);
        verify(itemService).insert(line.capture());
        assertEquals(new BigDecimal(quantity), line.getValue().orderedQty());
    }

    @Test
    void rejectsInvalidQuantityOnDraftUpdateBeforeChangingHeaderOrLines() {
        unit.setQuantityPrecision(2);
        MesSalesOrderEntity existing = new MesSalesOrderEntity();
        existing.setId(101L);
        existing.setCustomerId(3L);
        existing.setSourceType("LOCAL");
        existing.setStatus("DRAFT");
        existing.setRevision(4);
        existing.setRemark("retained draft");
        when(orders.selectByPrimaryKeyForUpdate(101L)).thenReturn(existing);
        SalesOrderUpdateRequest request = new SalesOrderUpdateRequest(4, 3L, LocalDate.of(2026, 10, 28),
                "must not be saved", List.of(line("1.234")));

        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(FoundationException.class,
                () -> service.update(101L, request)).code());

        assertEquals(4, existing.getRevision());
        assertEquals("retained draft", existing.getRemark());
        verify(orders, never()).updateByPrimaryKeySelective(any());
        verifyNoInteractions(items, itemService);
    }

    private static SalesOrderCreateRequest request(String quantity) {
        return new SalesOrderCreateRequest(3L, LocalDate.of(2026, 10, 28), null, List.of(line(quantity)));
    }

    private static SalesOrderLineRequest line(String quantity) {
        return new SalesOrderLineRequest(null, 10L, new BigDecimal(quantity), null, null);
    }
}
