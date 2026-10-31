// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesProductionCombinationExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesProductionCombinationExample() {
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

        public Criteria andProductIdIsNull() {
            addCriterion("product_id is null");
            return (Criteria) this;
        }

        public Criteria andProductIdIsNotNull() {
            addCriterion("product_id is not null");
            return (Criteria) this;
        }

        public Criteria andProductIdEqualTo(Long value) {
            addCriterion("product_id =", value, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdNotEqualTo(Long value) {
            addCriterion("product_id <>", value, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdGreaterThan(Long value) {
            addCriterion("product_id >", value, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdGreaterThanOrEqualTo(Long value) {
            addCriterion("product_id >=", value, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdLessThan(Long value) {
            addCriterion("product_id <", value, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdLessThanOrEqualTo(Long value) {
            addCriterion("product_id <=", value, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdIn(List<Long> values) {
            addCriterion("product_id in", values, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdNotIn(List<Long> values) {
            addCriterion("product_id not in", values, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdBetween(Long value1, Long value2) {
            addCriterion("product_id between", value1, value2, "productId");
            return (Criteria) this;
        }

        public Criteria andProductIdNotBetween(Long value1, Long value2) {
            addCriterion("product_id not between", value1, value2, "productId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdIsNull() {
            addCriterion("output_unit_id is null");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdIsNotNull() {
            addCriterion("output_unit_id is not null");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdEqualTo(Long value) {
            addCriterion("output_unit_id =", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdNotEqualTo(Long value) {
            addCriterion("output_unit_id <>", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdGreaterThan(Long value) {
            addCriterion("output_unit_id >", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdGreaterThanOrEqualTo(Long value) {
            addCriterion("output_unit_id >=", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdLessThan(Long value) {
            addCriterion("output_unit_id <", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdLessThanOrEqualTo(Long value) {
            addCriterion("output_unit_id <=", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdIn(List<Long> values) {
            addCriterion("output_unit_id in", values, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdNotIn(List<Long> values) {
            addCriterion("output_unit_id not in", values, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdBetween(Long value1, Long value2) {
            addCriterion("output_unit_id between", value1, value2, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdNotBetween(Long value1, Long value2) {
            addCriterion("output_unit_id not between", value1, value2, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionIsNull() {
            addCriterion("demand_revision is null");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionIsNotNull() {
            addCriterion("demand_revision is not null");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionEqualTo(Long value) {
            addCriterion("demand_revision =", value, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionNotEqualTo(Long value) {
            addCriterion("demand_revision <>", value, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionGreaterThan(Long value) {
            addCriterion("demand_revision >", value, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionGreaterThanOrEqualTo(Long value) {
            addCriterion("demand_revision >=", value, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionLessThan(Long value) {
            addCriterion("demand_revision <", value, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionLessThanOrEqualTo(Long value) {
            addCriterion("demand_revision <=", value, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionIn(List<Long> values) {
            addCriterion("demand_revision in", values, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionNotIn(List<Long> values) {
            addCriterion("demand_revision not in", values, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionBetween(Long value1, Long value2) {
            addCriterion("demand_revision between", value1, value2, "demandRevision");
            return (Criteria) this;
        }

        public Criteria andDemandRevisionNotBetween(Long value1, Long value2) {
            addCriterion("demand_revision not between", value1, value2, "demandRevision");
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

        public Criteria andFingerprintIsNull() {
            addCriterion("fingerprint is null");
            return (Criteria) this;
        }

        public Criteria andFingerprintIsNotNull() {
            addCriterion("fingerprint is not null");
            return (Criteria) this;
        }

        public Criteria andFingerprintEqualTo(String value) {
            addCriterion("fingerprint =", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintNotEqualTo(String value) {
            addCriterion("fingerprint <>", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintGreaterThan(String value) {
            addCriterion("fingerprint >", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintGreaterThanOrEqualTo(String value) {
            addCriterion("fingerprint >=", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintLessThan(String value) {
            addCriterion("fingerprint <", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintLessThanOrEqualTo(String value) {
            addCriterion("fingerprint <=", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintLike(String value) {
            addCriterion("fingerprint like", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintNotLike(String value) {
            addCriterion("fingerprint not like", value, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintIn(List<String> values) {
            addCriterion("fingerprint in", values, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintNotIn(List<String> values) {
            addCriterion("fingerprint not in", values, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintBetween(String value1, String value2) {
            addCriterion("fingerprint between", value1, value2, "fingerprint");
            return (Criteria) this;
        }

        public Criteria andFingerprintNotBetween(String value1, String value2) {
            addCriterion("fingerprint not between", value1, value2, "fingerprint");
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

        public Criteria andCreatedByIsNull() {
            addCriterion("created_by is null");
            return (Criteria) this;
        }

        public Criteria andCreatedByIsNotNull() {
            addCriterion("created_by is not null");
            return (Criteria) this;
        }

        public Criteria andCreatedByEqualTo(Long value) {
            addCriterion("created_by =", value, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByNotEqualTo(Long value) {
            addCriterion("created_by <>", value, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByGreaterThan(Long value) {
            addCriterion("created_by >", value, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByGreaterThanOrEqualTo(Long value) {
            addCriterion("created_by >=", value, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByLessThan(Long value) {
            addCriterion("created_by <", value, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByLessThanOrEqualTo(Long value) {
            addCriterion("created_by <=", value, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByIn(List<Long> values) {
            addCriterion("created_by in", values, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByNotIn(List<Long> values) {
            addCriterion("created_by not in", values, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByBetween(Long value1, Long value2) {
            addCriterion("created_by between", value1, value2, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedByNotBetween(Long value1, Long value2) {
            addCriterion("created_by not between", value1, value2, "createdBy");
            return (Criteria) this;
        }

        public Criteria andCreatedAtIsNull() {
            addCriterion("created_at is null");
            return (Criteria) this;
        }

        public Criteria andCreatedAtIsNotNull() {
            addCriterion("created_at is not null");
            return (Criteria) this;
        }

        public Criteria andCreatedAtEqualTo(LocalDateTime value) {
            addCriterion("created_at =", value, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtNotEqualTo(LocalDateTime value) {
            addCriterion("created_at <>", value, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtGreaterThan(LocalDateTime value) {
            addCriterion("created_at >", value, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("created_at >=", value, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtLessThan(LocalDateTime value) {
            addCriterion("created_at <", value, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("created_at <=", value, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtIn(List<LocalDateTime> values) {
            addCriterion("created_at in", values, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtNotIn(List<LocalDateTime> values) {
            addCriterion("created_at not in", values, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("created_at between", value1, value2, "createdAt");
            return (Criteria) this;
        }

        public Criteria andCreatedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("created_at not between", value1, value2, "createdAt");
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