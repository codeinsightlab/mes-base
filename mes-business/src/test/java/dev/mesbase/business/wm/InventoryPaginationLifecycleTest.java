package dev.mesbase.business.wm;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmInventoryQueryDto;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import dev.mesbase.business.wm.sync.WmInventoryQueryPort;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** TEST ONLY: mapper stubs deliberately bypass the interceptor's automatic cleanup. */
class InventoryPaginationLifecycleTest {
    private final WmInventoryMapper mapper = mock(WmInventoryMapper.class);
    private final StockReferenceRules rules = mock(StockReferenceRules.class);
    private final WmInventoryServiceImpl service = new WmInventoryServiceImpl(mapper,
            new DefaultListableBeanFactory().getBeanProvider(WmInventoryQueryPort.class),
            null, null, null, null, rules);
    private final WmInventoryQueryDto query = new WmInventoryQueryDto(1, 1, null, null, null, null, null, null);

    @AfterEach
    void clearTestThread() {
        PageHelper.clearPage();
    }

    @Test
    void closesPageBeforeReferenceQueriesAndKeepsTotal() {
        var rows = List.of(new WmInventoryEntity());
        when(mapper.selectByExample(any(WmInventoryExample.class))).thenAnswer(call -> {
            Page<?> page = PageHelper.getLocalPage();
            assertNotNull(page);
            assertEquals(1, page.getStartRow());
            page.setTotal(3);
            return rows;
        });
        when(rules.describe(rows)).thenAnswer(call -> {
            assertNull(PageHelper.getLocalPage());
            return List.of();
        });
        var result = service.selectPage(query);
        assertEquals(3, result.total());
        assertEquals(1, result.offset());
        assertEquals(1, result.limit());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void failureBeforeInterceptorDoesNotLeakIntoNextCall() {
        var failure = new IllegalStateException("TEST ONLY before interceptor");
        when(mapper.selectByExample(any(WmInventoryExample.class))).thenAnswer(call -> {
            assertNotNull(PageHelper.getLocalPage());
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.selectPage(query)));
        assertNull(PageHelper.getLocalPage());
        verifyNoInteractions(rules);
        doAnswer(call -> {
            PageHelper.getLocalPage().setTotal(0);
            return List.of();
        }).when(mapper).selectByExample(any(WmInventoryExample.class));
        when(rules.describe(List.of())).thenReturn(List.of());
        assertEquals(0, service.selectPage(query).total());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void referenceFailurePropagatesWithPaginationAlreadyClosed() {
        when(mapper.selectByExample(any(WmInventoryExample.class))).thenReturn(List.of());
        var failure = new IllegalStateException("TEST ONLY reference lookup");
        when(rules.describe(List.of())).thenAnswer(call -> {
            assertNull(PageHelper.getLocalPage());
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.selectPage(query)));
        assertNull(PageHelper.getLocalPage());
    }
}
