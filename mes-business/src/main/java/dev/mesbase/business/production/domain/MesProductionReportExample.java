// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesProductionReportExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesProductionReportExample() {
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

        public Criteria andExecutionIdIsNull() {
            addCriterion("execution_id is null");
            return (Criteria) this;
        }

        public Criteria andExecutionIdIsNotNull() {
            addCriterion("execution_id is not null");
            return (Criteria) this;
        }

        public Criteria andExecutionIdEqualTo(Long value) {
            addCriterion("execution_id =", value, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdNotEqualTo(Long value) {
            addCriterion("execution_id <>", value, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdGreaterThan(Long value) {
            addCriterion("execution_id >", value, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdGreaterThanOrEqualTo(Long value) {
            addCriterion("execution_id >=", value, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdLessThan(Long value) {
            addCriterion("execution_id <", value, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdLessThanOrEqualTo(Long value) {
            addCriterion("execution_id <=", value, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdIn(List<Long> values) {
            addCriterion("execution_id in", values, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdNotIn(List<Long> values) {
            addCriterion("execution_id not in", values, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdBetween(Long value1, Long value2) {
            addCriterion("execution_id between", value1, value2, "executionId");
            return (Criteria) this;
        }

        public Criteria andExecutionIdNotBetween(Long value1, Long value2) {
            addCriterion("execution_id not between", value1, value2, "executionId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdIsNull() {
            addCriterion("routing_node_id is null");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdIsNotNull() {
            addCriterion("routing_node_id is not null");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdEqualTo(Long value) {
            addCriterion("routing_node_id =", value, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdNotEqualTo(Long value) {
            addCriterion("routing_node_id <>", value, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdGreaterThan(Long value) {
            addCriterion("routing_node_id >", value, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdGreaterThanOrEqualTo(Long value) {
            addCriterion("routing_node_id >=", value, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdLessThan(Long value) {
            addCriterion("routing_node_id <", value, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdLessThanOrEqualTo(Long value) {
            addCriterion("routing_node_id <=", value, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdIn(List<Long> values) {
            addCriterion("routing_node_id in", values, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdNotIn(List<Long> values) {
            addCriterion("routing_node_id not in", values, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdBetween(Long value1, Long value2) {
            addCriterion("routing_node_id between", value1, value2, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRoutingNodeIdNotBetween(Long value1, Long value2) {
            addCriterion("routing_node_id not between", value1, value2, "routingNodeId");
            return (Criteria) this;
        }

        public Criteria andRecordTypeIsNull() {
            addCriterion("record_type is null");
            return (Criteria) this;
        }

        public Criteria andRecordTypeIsNotNull() {
            addCriterion("record_type is not null");
            return (Criteria) this;
        }

        public Criteria andRecordTypeEqualTo(String value) {
            addCriterion("record_type =", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeNotEqualTo(String value) {
            addCriterion("record_type <>", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeGreaterThan(String value) {
            addCriterion("record_type >", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeGreaterThanOrEqualTo(String value) {
            addCriterion("record_type >=", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeLessThan(String value) {
            addCriterion("record_type <", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeLessThanOrEqualTo(String value) {
            addCriterion("record_type <=", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeLike(String value) {
            addCriterion("record_type like", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeNotLike(String value) {
            addCriterion("record_type not like", value, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeIn(List<String> values) {
            addCriterion("record_type in", values, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeNotIn(List<String> values) {
            addCriterion("record_type not in", values, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeBetween(String value1, String value2) {
            addCriterion("record_type between", value1, value2, "recordType");
            return (Criteria) this;
        }

        public Criteria andRecordTypeNotBetween(String value1, String value2) {
            addCriterion("record_type not between", value1, value2, "recordType");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaIsNull() {
            addCriterion("quantity_delta is null");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaIsNotNull() {
            addCriterion("quantity_delta is not null");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaEqualTo(BigDecimal value) {
            addCriterion("quantity_delta =", value, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaNotEqualTo(BigDecimal value) {
            addCriterion("quantity_delta <>", value, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaGreaterThan(BigDecimal value) {
            addCriterion("quantity_delta >", value, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("quantity_delta >=", value, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaLessThan(BigDecimal value) {
            addCriterion("quantity_delta <", value, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaLessThanOrEqualTo(BigDecimal value) {
            addCriterion("quantity_delta <=", value, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaIn(List<BigDecimal> values) {
            addCriterion("quantity_delta in", values, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaNotIn(List<BigDecimal> values) {
            addCriterion("quantity_delta not in", values, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("quantity_delta between", value1, value2, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityDeltaNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("quantity_delta not between", value1, value2, "quantityDelta");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdIsNull() {
            addCriterion("reference_report_id is null");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdIsNotNull() {
            addCriterion("reference_report_id is not null");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdEqualTo(Long value) {
            addCriterion("reference_report_id =", value, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdNotEqualTo(Long value) {
            addCriterion("reference_report_id <>", value, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdGreaterThan(Long value) {
            addCriterion("reference_report_id >", value, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdGreaterThanOrEqualTo(Long value) {
            addCriterion("reference_report_id >=", value, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdLessThan(Long value) {
            addCriterion("reference_report_id <", value, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdLessThanOrEqualTo(Long value) {
            addCriterion("reference_report_id <=", value, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdIn(List<Long> values) {
            addCriterion("reference_report_id in", values, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdNotIn(List<Long> values) {
            addCriterion("reference_report_id not in", values, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdBetween(Long value1, Long value2) {
            addCriterion("reference_report_id between", value1, value2, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andReferenceReportIdNotBetween(Long value1, Long value2) {
            addCriterion("reference_report_id not between", value1, value2, "referenceReportId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdIsNull() {
            addCriterion("operator_user_id is null");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdIsNotNull() {
            addCriterion("operator_user_id is not null");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdEqualTo(Long value) {
            addCriterion("operator_user_id =", value, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdNotEqualTo(Long value) {
            addCriterion("operator_user_id <>", value, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdGreaterThan(Long value) {
            addCriterion("operator_user_id >", value, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdGreaterThanOrEqualTo(Long value) {
            addCriterion("operator_user_id >=", value, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdLessThan(Long value) {
            addCriterion("operator_user_id <", value, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdLessThanOrEqualTo(Long value) {
            addCriterion("operator_user_id <=", value, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdIn(List<Long> values) {
            addCriterion("operator_user_id in", values, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdNotIn(List<Long> values) {
            addCriterion("operator_user_id not in", values, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdBetween(Long value1, Long value2) {
            addCriterion("operator_user_id between", value1, value2, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andOperatorUserIdNotBetween(Long value1, Long value2) {
            addCriterion("operator_user_id not between", value1, value2, "operatorUserId");
            return (Criteria) this;
        }

        public Criteria andRecordedByIsNull() {
            addCriterion("recorded_by is null");
            return (Criteria) this;
        }

        public Criteria andRecordedByIsNotNull() {
            addCriterion("recorded_by is not null");
            return (Criteria) this;
        }

        public Criteria andRecordedByEqualTo(Long value) {
            addCriterion("recorded_by =", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByNotEqualTo(Long value) {
            addCriterion("recorded_by <>", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByGreaterThan(Long value) {
            addCriterion("recorded_by >", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByGreaterThanOrEqualTo(Long value) {
            addCriterion("recorded_by >=", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByLessThan(Long value) {
            addCriterion("recorded_by <", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByLessThanOrEqualTo(Long value) {
            addCriterion("recorded_by <=", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByIn(List<Long> values) {
            addCriterion("recorded_by in", values, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByNotIn(List<Long> values) {
            addCriterion("recorded_by not in", values, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByBetween(Long value1, Long value2) {
            addCriterion("recorded_by between", value1, value2, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByNotBetween(Long value1, Long value2) {
            addCriterion("recorded_by not between", value1, value2, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andReportTimeIsNull() {
            addCriterion("report_time is null");
            return (Criteria) this;
        }

        public Criteria andReportTimeIsNotNull() {
            addCriterion("report_time is not null");
            return (Criteria) this;
        }

        public Criteria andReportTimeEqualTo(LocalDateTime value) {
            addCriterion("report_time =", value, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeNotEqualTo(LocalDateTime value) {
            addCriterion("report_time <>", value, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeGreaterThan(LocalDateTime value) {
            addCriterion("report_time >", value, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("report_time >=", value, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeLessThan(LocalDateTime value) {
            addCriterion("report_time <", value, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("report_time <=", value, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeIn(List<LocalDateTime> values) {
            addCriterion("report_time in", values, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeNotIn(List<LocalDateTime> values) {
            addCriterion("report_time not in", values, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("report_time between", value1, value2, "reportTime");
            return (Criteria) this;
        }

        public Criteria andReportTimeNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("report_time not between", value1, value2, "reportTime");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdIsNull() {
            addCriterion("workstation_id is null");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdIsNotNull() {
            addCriterion("workstation_id is not null");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdEqualTo(Long value) {
            addCriterion("workstation_id =", value, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdNotEqualTo(Long value) {
            addCriterion("workstation_id <>", value, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdGreaterThan(Long value) {
            addCriterion("workstation_id >", value, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdGreaterThanOrEqualTo(Long value) {
            addCriterion("workstation_id >=", value, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdLessThan(Long value) {
            addCriterion("workstation_id <", value, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdLessThanOrEqualTo(Long value) {
            addCriterion("workstation_id <=", value, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdIn(List<Long> values) {
            addCriterion("workstation_id in", values, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdNotIn(List<Long> values) {
            addCriterion("workstation_id not in", values, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdBetween(Long value1, Long value2) {
            addCriterion("workstation_id between", value1, value2, "workstationId");
            return (Criteria) this;
        }

        public Criteria andWorkstationIdNotBetween(Long value1, Long value2) {
            addCriterion("workstation_id not between", value1, value2, "workstationId");
            return (Criteria) this;
        }

        public Criteria andReasonIsNull() {
            addCriterion("reason is null");
            return (Criteria) this;
        }

        public Criteria andReasonIsNotNull() {
            addCriterion("reason is not null");
            return (Criteria) this;
        }

        public Criteria andReasonEqualTo(String value) {
            addCriterion("reason =", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonNotEqualTo(String value) {
            addCriterion("reason <>", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonGreaterThan(String value) {
            addCriterion("reason >", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonGreaterThanOrEqualTo(String value) {
            addCriterion("reason >=", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonLessThan(String value) {
            addCriterion("reason <", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonLessThanOrEqualTo(String value) {
            addCriterion("reason <=", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonLike(String value) {
            addCriterion("reason like", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonNotLike(String value) {
            addCriterion("reason not like", value, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonIn(List<String> values) {
            addCriterion("reason in", values, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonNotIn(List<String> values) {
            addCriterion("reason not in", values, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonBetween(String value1, String value2) {
            addCriterion("reason between", value1, value2, "reason");
            return (Criteria) this;
        }

        public Criteria andReasonNotBetween(String value1, String value2) {
            addCriterion("reason not between", value1, value2, "reason");
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