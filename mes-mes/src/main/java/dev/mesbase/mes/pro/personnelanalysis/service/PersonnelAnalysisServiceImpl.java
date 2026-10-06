package dev.mesbase.mes.pro.personnelanalysis.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.enums.UserStatus;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.mes.pro.domain.PageResult;
import dev.mesbase.mes.pro.personnelanalysis.domain.*;
import dev.mesbase.mes.pro.personnelanalysis.mapper.ProReportingWorkPersonnelExtraMapper;
import dev.mesbase.system.domain.dto.UserLookupQuery;
import dev.mesbase.system.mapper.SysUserDisplayExtraMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.analytics.enabled",havingValue="true")
@Service
public class PersonnelAnalysisServiceImpl implements PersonnelAnalysisService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ProReportingWorkPersonnelExtraMapper reporting;
    private final SysUserDisplayExtraMapper users;

    public PersonnelAnalysisServiceImpl(ProReportingWorkPersonnelExtraMapper reporting, SysUserDisplayExtraMapper users) {
        this.reporting = reporting;
        this.users = users;
    }

    @Override
    public PageInfo<PersonnelRecordVO> records(PersonnelAnalysisQueryDTO request,Long processId,String itemCode) {
        PersonnelQuery query=query(request);requireUser(query);query.setProcessId(processId);query.setItemCode(itemCode);
        PageHelper.startPage(pageNum(request),pageSize(request));return new PageInfo<>(reporting.selectRecords(query));
    }

    @Override
    public PageResult<PersonnelAccountVO> accounts(PersonnelAnalysisQueryDTO request) {
        PersonnelQuery query = query(request);
        if (query.getAccountKeyword() != null) {
            UserLookupQuery search = new UserLookupQuery();
            search.setKeyword(query.getAccountKeyword());
            query.setMatchingUserIds(users.selectPersonnelUserIdsByKeyword(search));
        }
        long total = reporting.countAccounts(query);
        PageHelper.startPage(pageNum(request), pageSize(request), false);
        List<PersonnelAccountRow> rows = reporting.selectAccounts(query);
        Map<Long, SysUser> currentUsers = currentUsersById(rows.stream().map(PersonnelAccountRow::getUserId).collect(Collectors.toList()));
        List<PersonnelAccountVO> values = new ArrayList<>(rows.size());
        for (PersonnelAccountRow row : rows) values.add(account(row, currentUsers.get(row.getUserId()), query));
        return page(values, total, request);
    }

    @Override
    public PersonnelAccountVO overview(PersonnelAnalysisQueryDTO request) {
        PersonnelQuery query = query(request);
        requireUser(query);
        PersonnelAccountRow row = reporting.selectAccount(query);
        if (row == null) throw new ServiceException("该账号在当前工厂和时间范围内没有报工记录");
        List<Long> ids = new ArrayList<>(1);
        ids.add(row.getUserId());
        return account(row, currentUsersById(ids).get(row.getUserId()), query);
    }

    @Override
    public PageResult<PersonnelProcessVO> processes(PersonnelAnalysisQueryDTO request) {
        PersonnelQuery query = query(request);
        requireUser(query);
        PageHelper.startPage(pageNum(request), pageSize(request));
        List<PersonnelProcessRow> rows = reporting.selectProcesses(query);
        long total = new PageInfo<>(rows).getTotal();
        List<PersonnelProcessVO> values = new ArrayList<>(rows.size());
        for (PersonnelProcessRow row : rows) {
            PersonnelProcessVO value = new PersonnelProcessVO();
            value.setProcessId(row.getProcessId());
            value.setProcessCode(row.getProcessCode());
            value.setProcessName(row.getProcessName());
            value.setActiveMonths(row.getActiveMonths());
            value.setProductCount(row.getProductCount());
            value.setWorkorderCount(row.getWorkorderCount());
            value.setReportRecordCount(row.getReportRecordCount());
            value.setLastReportTime(row.getLastReportTime());
            values.add(value);
        }
        return page(values, total, request);
    }

    @Override
    public PageResult<PersonnelProductVO> products(PersonnelAnalysisQueryDTO request) {
        PersonnelQuery query = query(request);
        requireUser(query);
        PageHelper.startPage(pageNum(request), pageSize(request));
        List<PersonnelProductRow> rows = reporting.selectProducts(query);
        long total = new PageInfo<>(rows).getTotal();
        List<PersonnelProductVO> values = new ArrayList<>(rows.size());
        for (PersonnelProductRow row : rows) {
            PersonnelProductVO value = new PersonnelProductVO();
            value.setItemCode(row.getItemCode());
            value.setItemName(row.getItemName());
            value.setActiveMonths(row.getActiveMonths());
            value.setProcessCount(row.getProcessCount());
            value.setReportRecordCount(row.getReportRecordCount());
            value.setLastReportTime(row.getLastReportTime());
            values.add(value);
        }
        return page(values, total, request);
    }

    @Override
    public List<PersonnelTrendVO> trend(PersonnelAnalysisQueryDTO request) {
        PersonnelQuery query = query(request);
        requireUser(query);
        LocalDateTime retentionStart = reporting.selectFirstRecordTime();
        List<PersonnelTrendRow> rows = reporting.selectTrend(query);
        List<PersonnelTrendVO> values = new ArrayList<>();
        if (retentionStart == null) return values;
        YearMonth first = YearMonth.from(query.getStartTime() == null || retentionStart.isAfter(query.getStartTime())
                ? retentionStart : query.getStartTime());
        YearMonth last = YearMonth.from(query.getEndTime().minusNanos(1));
        if (first.isAfter(last)) return values;
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            PersonnelTrendRow row = null;
            for (PersonnelTrendRow candidate : rows) if (month.toString().equals(candidate.getMonth())) { row = candidate; break; }
            PersonnelTrendVO value = new PersonnelTrendVO();
            value.setMonth(month.toString());
            value.setActiveDayCount(row == null ? 0L : row.getActiveDayCount());
            value.setReportRecordCount(row == null ? 0L : row.getReportRecordCount());
            value.setProcessCount(row == null ? 0L : row.getProcessCount());
            value.setProductCount(row == null ? 0L : row.getProductCount());
            value.setPartialMonth(month.equals(YearMonth.from(LocalDateTime.now())));
            values.add(value);
        }
        return values;
    }

    private PersonnelQuery query(PersonnelAnalysisQueryDTO request) {
        FactoryContext.required();
        PersonnelRange range = PersonnelRange.fromCode(request.getRange());
        LocalDateTime now = LocalDateTime.now();
        PersonnelQuery query = new PersonnelQuery();
        if (range == PersonnelRange.CUSTOM) {
            if (request.getStartTime() == null || request.getEndTime() == null)
                throw new ServiceException("自定义范围需要开始与结束时间");
            try {
                query.setStartTime(LocalDateTime.parse(request.getStartTime(), TIME_FORMAT));
                query.setEndTime(LocalDateTime.parse(request.getEndTime(), TIME_FORMAT));
            } catch (DateTimeParseException e) {
                throw new ServiceException("时间格式应为 yyyy-MM-dd HH:mm:ss");
            }
            if (!query.getStartTime().isBefore(query.getEndTime())) throw new ServiceException("开始时间必须早于结束时间");
            if (query.getEndTime().isAfter(now)) query.setEndTime(now);
            if (!query.getStartTime().isBefore(query.getEndTime())) throw new ServiceException("所选时间范围没有已发生的报工时间");
        } else {
            if (range.getMonths() > 0) query.setStartTime(now.toLocalDate().withDayOfMonth(1).minusMonths(range.getMonths() - 1).atStartOfDay());
            query.setEndTime(now);
        }
        query.setUserId(request.getUserId());
        query.setProcessId(request.getProcessId());
        query.setItemCode(request.getItemCode());
        if (request.getAccountKeyword() != null && !request.getAccountKeyword().trim().isEmpty())
            query.setAccountKeyword(request.getAccountKeyword().trim());
        return query;
    }

    private int pageNum(PersonnelAnalysisQueryDTO request) {
        return request.getPageNum() == null || request.getPageNum() < 1 ? 1 : request.getPageNum();
    }

    private int pageSize(PersonnelAnalysisQueryDTO request) {
        return request.getPageSize() == null || request.getPageSize() < 1 ? 30 : Math.min(request.getPageSize(), 100);
    }

    private void requireUser(PersonnelQuery query) {
        if (query.getUserId() == null || query.getUserId() <= 0) throw new ServiceException("账号 ID 无效");
    }

    private Map<Long, SysUser> currentUsersById(List<Long> ids) {
        Map<Long, SysUser> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        UserLookupQuery query = new UserLookupQuery();
        query.setUserIds(ids);
        for (SysUser user : users.selectPersonnelUsers(query)) result.put(user.getUserId(), user);
        return result;
    }

    private PersonnelAccountVO account(PersonnelAccountRow row, SysUser user, PersonnelQuery query) {
        PersonnelAccountVO value = new PersonnelAccountVO();
        value.setUserId(row.getUserId());
        if (user != null) {
            value.setUserName(user.getUserName());
            value.setNickName(user.getNickName());
            value.setAccountStatus(user.getStatus());
            value.setAccountStatusName(UserStatus.DISABLE.getCode().equals(user.getStatus()) ? "已停用"
                    : UserStatus.OK.getCode().equals(user.getStatus()) ? "启用" : "状态未知");
        } else value.setAccountStatusName("历史账号");
        value.setActiveMonths(row.getActiveMonths());
        value.setProcessCount(row.getProcessCount());
        value.setProductCount(row.getProductCount());
        value.setReportRecordCount(row.getReportRecordCount());
        value.setFirstReportTime(row.getFirstReportTime());
        value.setLastReportTime(row.getLastReportTime());
        value.setDataThrough(row.getLastReportTime());
        value.setGeneratedAt(LocalDateTime.now());
        value.setRangeStart(query.getStartTime());
        value.setRangeEndExclusive(query.getEndTime());
        return value;
    }

    private <T> PageResult<T> page(List<T> rows, long total, PersonnelAnalysisQueryDTO request) {
        PageResult<T> result = new PageResult<>();
        result.setRows(rows);
        result.setTotal(total);
        result.setPageNum(pageNum(request));
        result.setPageSize(pageSize(request));
        return result;
    }
}
