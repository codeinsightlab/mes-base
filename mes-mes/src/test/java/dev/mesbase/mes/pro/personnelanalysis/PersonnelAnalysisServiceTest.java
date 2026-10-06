package dev.mesbase.mes.pro.personnelanalysis;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.page.PageParams;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.mes.pro.domain.PageResult;
import dev.mesbase.mes.pro.personnelanalysis.domain.*;
import dev.mesbase.mes.pro.personnelanalysis.mapper.ProReportingWorkPersonnelExtraMapper;
import dev.mesbase.mes.pro.personnelanalysis.service.PersonnelAnalysisServiceImpl;
import dev.mesbase.system.domain.dto.UserLookupQuery;
import dev.mesbase.system.mapper.SysUserDisplayExtraMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.session.RowBounds;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonnelAnalysisServiceTest {
    private final ProReportingWorkPersonnelExtraMapper reporting = mock(ProReportingWorkPersonnelExtraMapper.class);
    private final SysUserDisplayExtraMapper users = mock(SysUserDisplayExtraMapper.class);
    private PersonnelAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        FactoryContext.bind(new dev.mesbase.core.factory.FactoryScope(new dev.mesbase.core.factory.FactoryId("1")));
        service = new PersonnelAnalysisServiceImpl(reporting, users);
    }

    @AfterEach
    void tearDown() { PageHelper.clearPage(); FactoryContext.clear(); }

    @Test
    void accountPageKeepsDatabaseTotalAndBatchesCurrentMasterUsers() {
        Page<PersonnelAccountRow> page = new Page<>(1, 30);
        page.setTotal(237);
        PersonnelAccountRow current = accountRow(10L);
        PersonnelAccountRow missing = accountRow(11L);
        page.add(current); page.add(missing);
        when(reporting.countAccounts(any(PersonnelQuery.class))).thenReturn(237L);
        when(reporting.selectAccounts(any(PersonnelQuery.class))).thenReturn(page);
        SysUser user = new SysUser(); user.setUserId(10L); user.setUserName("account10"); user.setStatus("1");
        when(users.selectPersonnelUsers(any(UserLookupQuery.class))).thenReturn(Collections.singletonList(user));

        PageResult<PersonnelAccountVO> result = service.accounts(new PersonnelAnalysisQueryDTO());

        assertEquals(237L, result.getTotal());
        verify(reporting).countAccounts(any(PersonnelQuery.class));
        assertEquals(2, result.getRows().size());
        assertEquals("已停用", result.getRows().get(0).getAccountStatusName());
        assertEquals("历史账号", result.getRows().get(1).getAccountStatusName());
        ArgumentCaptor<UserLookupQuery> captured = ArgumentCaptor.forClass(UserLookupQuery.class);
        verify(users, times(1)).selectPersonnelUsers(captured.capture());
        assertEquals(Arrays.asList(10L, 11L), captured.getValue().getUserIds());
    }

    @Test
    void accountCountQueryDoesNotTriggerArgumentBasedPagination() {
        PageParams params = new PageParams();
        Properties properties = new Properties();
        properties.setProperty("supportMethodsArguments", "true");
        params.setProperties(properties);

        assertNull(params.getPage(new PersonnelQuery(), RowBounds.DEFAULT));

        PageHelper.startPage(2, 30, false);
        Page<?> page = params.getPage(new PersonnelQuery(), RowBounds.DEFAULT);
        assertEquals(2, page.getPageNum());
        assertEquals(30, page.getPageSize());
        assertFalse(page.isCount());
    }

    @Test
    void trendFillsMissingMonthsOnlyFromFactoryRetentionStart() {
        when(reporting.selectFirstRecordTime()).thenReturn(LocalDateTime.of(2026, 1, 12, 8, 0));
        PersonnelTrendRow january = new PersonnelTrendRow(); january.setMonth("2026-01"); january.setActiveDayCount(2L);
        january.setReportRecordCount(5L); january.setProcessCount(1L); january.setProductCount(1L);
        PersonnelTrendRow march = new PersonnelTrendRow(); march.setMonth("2026-03"); march.setActiveDayCount(4L);
        march.setReportRecordCount(7L); march.setProcessCount(2L); march.setProductCount(1L);
        when(reporting.selectTrend(any(PersonnelQuery.class))).thenReturn(Arrays.asList(january, march));
        PersonnelAnalysisQueryDTO request = new PersonnelAnalysisQueryDTO();
        request.setUserId(10L); request.setRange("CUSTOM");
        request.setStartTime("2025-12-01 00:00:00"); request.setEndTime("2026-04-01 00:00:00");

        List<PersonnelTrendVO> result = service.trend(request);

        assertEquals(3, result.size());
        assertEquals("2026-01", result.get(0).getMonth());
        assertEquals("2026-02", result.get(1).getMonth());
        assertEquals(0L, result.get(1).getActiveDayCount());
        assertEquals(4L, result.get(2).getActiveDayCount());
    }

    private PersonnelAccountRow accountRow(long userId) {
        PersonnelAccountRow row = new PersonnelAccountRow();
        row.setUserId(userId); row.setActiveMonths(1L); row.setProcessCount(1L); row.setProductCount(1L);
        row.setReportRecordCount(1L); row.setFirstReportTime(LocalDateTime.of(2026, 9, 1, 9, 0));
        row.setLastReportTime(LocalDateTime.of(2026, 9, 1, 9, 0));
        return row;
    }
}
