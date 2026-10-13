// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductionBatchExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public ProductionBatchExample() {
        oredCriteria = new ArrayList<>();
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andIdIsNull() {
            addCriterion("id is null");
            return (Criteria) this;
        }

        public Criteria andIdIsNotNull() {
            addCriterion("id is not null");
            return (Criteria) this;
        }

        public Criteria andIdEqualTo(Long value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(Long value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(Long value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(Long value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(Long value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(Long value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<Long> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<Long> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(Long value1, Long value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(Long value1, Long value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdIsNull() {
            addCriterion("workorder_id is null");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdIsNotNull() {
            addCriterion("workorder_id is not null");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdEqualTo(Long value) {
            addCriterion("workorder_id =", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdNotEqualTo(Long value) {
            addCriterion("workorder_id <>", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdGreaterThan(Long value) {
            addCriterion("workorder_id >", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdGreaterThanOrEqualTo(Long value) {
            addCriterion("workorder_id >=", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdLessThan(Long value) {
            addCriterion("workorder_id <", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdLessThanOrEqualTo(Long value) {
            addCriterion("workorder_id <=", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdIn(List<Long> values) {
            addCriterion("workorder_id in", values, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdNotIn(List<Long> values) {
            addCriterion("workorder_id not in", values, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdBetween(Long value1, Long value2) {
            addCriterion("workorder_id between", value1, value2, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdNotBetween(Long value1, Long value2) {
            addCriterion("workorder_id not between", value1, value2, "workorderId");
            return (Criteria) this;
        }

        public Criteria andBatchNoIsNull() {
            addCriterion("batch_no is null");
            return (Criteria) this;
        }

        public Criteria andBatchNoIsNotNull() {
            addCriterion("batch_no is not null");
            return (Criteria) this;
        }

        public Criteria andBatchNoEqualTo(String value) {
            addCriterion("batch_no =", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotEqualTo(String value) {
            addCriterion("batch_no <>", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoGreaterThan(String value) {
            addCriterion("batch_no >", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoGreaterThanOrEqualTo(String value) {
            addCriterion("batch_no >=", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoLessThan(String value) {
            addCriterion("batch_no <", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoLessThanOrEqualTo(String value) {
            addCriterion("batch_no <=", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoLike(String value) {
            addCriterion("batch_no like", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotLike(String value) {
            addCriterion("batch_no not like", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoIn(List<String> values) {
            addCriterion("batch_no in", values, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotIn(List<String> values) {
            addCriterion("batch_no not in", values, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoBetween(String value1, String value2) {
            addCriterion("batch_no between", value1, value2, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotBetween(String value1, String value2) {
            addCriterion("batch_no not between", value1, value2, "batchNo");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyIsNull() {
            addCriterion("planned_qty is null");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyIsNotNull() {
            addCriterion("planned_qty is not null");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyEqualTo(BigDecimal value) {
            addCriterion("planned_qty =", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyNotEqualTo(BigDecimal value) {
            addCriterion("planned_qty <>", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyGreaterThan(BigDecimal value) {
            addCriterion("planned_qty >", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("planned_qty >=", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyLessThan(BigDecimal value) {
            addCriterion("planned_qty <", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("planned_qty <=", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyIn(List<BigDecimal> values) {
            addCriterion("planned_qty in", values, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyNotIn(List<BigDecimal> values) {
            addCriterion("planned_qty not in", values, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("planned_qty between", value1, value2, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("planned_qty not between", value1, value2, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andStatusIsNull() {
            addCriterion("status is null");
            return (Criteria) this;
        }

        public Criteria andStatusIsNotNull() {
            addCriterion("status is not null");
            return (Criteria) this;
        }

        public Criteria andStatusEqualTo(String value) {
            addCriterion("status =", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotEqualTo(String value) {
            addCriterion("status <>", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThan(String value) {
            addCriterion("status >", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThanOrEqualTo(String value) {
            addCriterion("status >=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThan(String value) {
            addCriterion("status <", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThanOrEqualTo(String value) {
            addCriterion("status <=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLike(String value) {
            addCriterion("status like", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotLike(String value) {
            addCriterion("status not like", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusIn(List<String> values) {
            addCriterion("status in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotIn(List<String> values) {
            addCriterion("status not in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusBetween(String value1, String value2) {
            addCriterion("status between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotBetween(String value1, String value2) {
            addCriterion("status not between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andRouteStatusIsNull() {
            addCriterion("route_status is null");
            return (Criteria) this;
        }

        public Criteria andRouteStatusIsNotNull() {
            addCriterion("route_status is not null");
            return (Criteria) this;
        }

        public Criteria andRouteStatusEqualTo(String value) {
            addCriterion("route_status =", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusNotEqualTo(String value) {
            addCriterion("route_status <>", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusGreaterThan(String value) {
            addCriterion("route_status >", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusGreaterThanOrEqualTo(String value) {
            addCriterion("route_status >=", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusLessThan(String value) {
            addCriterion("route_status <", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusLessThanOrEqualTo(String value) {
            addCriterion("route_status <=", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusLike(String value) {
            addCriterion("route_status like", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusNotLike(String value) {
            addCriterion("route_status not like", value, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusIn(List<String> values) {
            addCriterion("route_status in", values, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusNotIn(List<String> values) {
            addCriterion("route_status not in", values, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusBetween(String value1, String value2) {
            addCriterion("route_status between", value1, value2, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andRouteStatusNotBetween(String value1, String value2) {
            addCriterion("route_status not between", value1, value2, "routeStatus");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdIsNull() {
            addCriterion("issue_command_id is null");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdIsNotNull() {
            addCriterion("issue_command_id is not null");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdEqualTo(String value) {
            addCriterion("issue_command_id =", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdNotEqualTo(String value) {
            addCriterion("issue_command_id <>", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdGreaterThan(String value) {
            addCriterion("issue_command_id >", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdGreaterThanOrEqualTo(String value) {
            addCriterion("issue_command_id >=", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdLessThan(String value) {
            addCriterion("issue_command_id <", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdLessThanOrEqualTo(String value) {
            addCriterion("issue_command_id <=", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdLike(String value) {
            addCriterion("issue_command_id like", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdNotLike(String value) {
            addCriterion("issue_command_id not like", value, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdIn(List<String> values) {
            addCriterion("issue_command_id in", values, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdNotIn(List<String> values) {
            addCriterion("issue_command_id not in", values, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdBetween(String value1, String value2) {
            addCriterion("issue_command_id between", value1, value2, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andIssueCommandIdNotBetween(String value1, String value2) {
            addCriterion("issue_command_id not between", value1, value2, "issueCommandId");
            return (Criteria) this;
        }

        public Criteria andLockedAtIsNull() {
            addCriterion("locked_at is null");
            return (Criteria) this;
        }

        public Criteria andLockedAtIsNotNull() {
            addCriterion("locked_at is not null");
            return (Criteria) this;
        }

        public Criteria andLockedAtEqualTo(LocalDateTime value) {
            addCriterion("locked_at =", value, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtNotEqualTo(LocalDateTime value) {
            addCriterion("locked_at <>", value, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtGreaterThan(LocalDateTime value) {
            addCriterion("locked_at >", value, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("locked_at >=", value, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtLessThan(LocalDateTime value) {
            addCriterion("locked_at <", value, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("locked_at <=", value, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtIn(List<LocalDateTime> values) {
            addCriterion("locked_at in", values, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtNotIn(List<LocalDateTime> values) {
            addCriterion("locked_at not in", values, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("locked_at between", value1, value2, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andLockedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("locked_at not between", value1, value2, "lockedAt");
            return (Criteria) this;
        }

        public Criteria andRevisionIsNull() {
            addCriterion("revision is null");
            return (Criteria) this;
        }

        public Criteria andRevisionIsNotNull() {
            addCriterion("revision is not null");
            return (Criteria) this;
        }

        public Criteria andRevisionEqualTo(Integer value) {
            addCriterion("revision =", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionNotEqualTo(Integer value) {
            addCriterion("revision <>", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionGreaterThan(Integer value) {
            addCriterion("revision >", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionGreaterThanOrEqualTo(Integer value) {
            addCriterion("revision >=", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionLessThan(Integer value) {
            addCriterion("revision <", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionLessThanOrEqualTo(Integer value) {
            addCriterion("revision <=", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionIn(List<Integer> values) {
            addCriterion("revision in", values, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionNotIn(List<Integer> values) {
            addCriterion("revision not in", values, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionBetween(Integer value1, Integer value2) {
            addCriterion("revision between", value1, value2, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionNotBetween(Integer value1, Integer value2) {
            addCriterion("revision not between", value1, value2, "revision");
            return (Criteria) this;
        }

        public Criteria andRemarkIsNull() {
            addCriterion("remark is null");
            return (Criteria) this;
        }

        public Criteria andRemarkIsNotNull() {
            addCriterion("remark is not null");
            return (Criteria) this;
        }

        public Criteria andRemarkEqualTo(String value) {
            addCriterion("remark =", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotEqualTo(String value) {
            addCriterion("remark <>", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkGreaterThan(String value) {
            addCriterion("remark >", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkGreaterThanOrEqualTo(String value) {
            addCriterion("remark >=", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkLessThan(String value) {
            addCriterion("remark <", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkLessThanOrEqualTo(String value) {
            addCriterion("remark <=", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkLike(String value) {
            addCriterion("remark like", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotLike(String value) {
            addCriterion("remark not like", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkIn(List<String> values) {
            addCriterion("remark in", values, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotIn(List<String> values) {
            addCriterion("remark not in", values, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkBetween(String value1, String value2) {
            addCriterion("remark between", value1, value2, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotBetween(String value1, String value2) {
            addCriterion("remark not between", value1, value2, "remark");
            return (Criteria) this;
        }

        public Criteria andCreateTimeIsNull() {
            addCriterion("create_time is null");
            return (Criteria) this;
        }

        public Criteria andCreateTimeIsNotNull() {
            addCriterion("create_time is not null");
            return (Criteria) this;
        }

        public Criteria andCreateTimeEqualTo(LocalDateTime value) {
            addCriterion("create_time =", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeNotEqualTo(LocalDateTime value) {
            addCriterion("create_time <>", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeGreaterThan(LocalDateTime value) {
            addCriterion("create_time >", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("create_time >=", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeLessThan(LocalDateTime value) {
            addCriterion("create_time <", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("create_time <=", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeIn(List<LocalDateTime> values) {
            addCriterion("create_time in", values, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeNotIn(List<LocalDateTime> values) {
            addCriterion("create_time not in", values, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("create_time between", value1, value2, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("create_time not between", value1, value2, "createTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeIsNull() {
            addCriterion("update_time is null");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeIsNotNull() {
            addCriterion("update_time is not null");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeEqualTo(LocalDateTime value) {
            addCriterion("update_time =", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeNotEqualTo(LocalDateTime value) {
            addCriterion("update_time <>", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeGreaterThan(LocalDateTime value) {
            addCriterion("update_time >", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("update_time >=", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeLessThan(LocalDateTime value) {
            addCriterion("update_time <", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("update_time <=", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeIn(List<LocalDateTime> values) {
            addCriterion("update_time in", values, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeNotIn(List<LocalDateTime> values) {
            addCriterion("update_time not in", values, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("update_time between", value1, value2, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("update_time not between", value1, value2, "updateTime");
            return (Criteria) this;
        }
    }

    public static class Criteria extends GeneratedCriteria {
        protected Criteria() {
            super();
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }
}