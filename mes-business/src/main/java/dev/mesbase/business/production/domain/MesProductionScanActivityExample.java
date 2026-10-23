// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesProductionScanActivityExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesProductionScanActivityExample() {
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

        public Criteria andIdEqualTo(String value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(String value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(String value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(String value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(String value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(String value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLike(String value) {
            addCriterion("id like", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotLike(String value) {
            addCriterion("id not like", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<String> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<String> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(String value1, String value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(String value1, String value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andScanTypeIsNull() {
            addCriterion("scan_type is null");
            return (Criteria) this;
        }

        public Criteria andScanTypeIsNotNull() {
            addCriterion("scan_type is not null");
            return (Criteria) this;
        }

        public Criteria andScanTypeEqualTo(String value) {
            addCriterion("scan_type =", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeNotEqualTo(String value) {
            addCriterion("scan_type <>", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeGreaterThan(String value) {
            addCriterion("scan_type >", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeGreaterThanOrEqualTo(String value) {
            addCriterion("scan_type >=", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeLessThan(String value) {
            addCriterion("scan_type <", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeLessThanOrEqualTo(String value) {
            addCriterion("scan_type <=", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeLike(String value) {
            addCriterion("scan_type like", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeNotLike(String value) {
            addCriterion("scan_type not like", value, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeIn(List<String> values) {
            addCriterion("scan_type in", values, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeNotIn(List<String> values) {
            addCriterion("scan_type not in", values, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeBetween(String value1, String value2) {
            addCriterion("scan_type between", value1, value2, "scanType");
            return (Criteria) this;
        }

        public Criteria andScanTypeNotBetween(String value1, String value2) {
            addCriterion("scan_type not between", value1, value2, "scanType");
            return (Criteria) this;
        }

        public Criteria andInputMethodIsNull() {
            addCriterion("input_method is null");
            return (Criteria) this;
        }

        public Criteria andInputMethodIsNotNull() {
            addCriterion("input_method is not null");
            return (Criteria) this;
        }

        public Criteria andInputMethodEqualTo(String value) {
            addCriterion("input_method =", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodNotEqualTo(String value) {
            addCriterion("input_method <>", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodGreaterThan(String value) {
            addCriterion("input_method >", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodGreaterThanOrEqualTo(String value) {
            addCriterion("input_method >=", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodLessThan(String value) {
            addCriterion("input_method <", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodLessThanOrEqualTo(String value) {
            addCriterion("input_method <=", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodLike(String value) {
            addCriterion("input_method like", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodNotLike(String value) {
            addCriterion("input_method not like", value, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodIn(List<String> values) {
            addCriterion("input_method in", values, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodNotIn(List<String> values) {
            addCriterion("input_method not in", values, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodBetween(String value1, String value2) {
            addCriterion("input_method between", value1, value2, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andInputMethodNotBetween(String value1, String value2) {
            addCriterion("input_method not between", value1, value2, "inputMethod");
            return (Criteria) this;
        }

        public Criteria andUserIdIsNull() {
            addCriterion("user_id is null");
            return (Criteria) this;
        }

        public Criteria andUserIdIsNotNull() {
            addCriterion("user_id is not null");
            return (Criteria) this;
        }

        public Criteria andUserIdEqualTo(Long value) {
            addCriterion("user_id =", value, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdNotEqualTo(Long value) {
            addCriterion("user_id <>", value, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdGreaterThan(Long value) {
            addCriterion("user_id >", value, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdGreaterThanOrEqualTo(Long value) {
            addCriterion("user_id >=", value, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdLessThan(Long value) {
            addCriterion("user_id <", value, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdLessThanOrEqualTo(Long value) {
            addCriterion("user_id <=", value, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdIn(List<Long> values) {
            addCriterion("user_id in", values, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdNotIn(List<Long> values) {
            addCriterion("user_id not in", values, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdBetween(Long value1, Long value2) {
            addCriterion("user_id between", value1, value2, "userId");
            return (Criteria) this;
        }

        public Criteria andUserIdNotBetween(Long value1, Long value2) {
            addCriterion("user_id not between", value1, value2, "userId");
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

        public Criteria andOutcomeIsNull() {
            addCriterion("outcome is null");
            return (Criteria) this;
        }

        public Criteria andOutcomeIsNotNull() {
            addCriterion("outcome is not null");
            return (Criteria) this;
        }

        public Criteria andOutcomeEqualTo(String value) {
            addCriterion("outcome =", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeNotEqualTo(String value) {
            addCriterion("outcome <>", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeGreaterThan(String value) {
            addCriterion("outcome >", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeGreaterThanOrEqualTo(String value) {
            addCriterion("outcome >=", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeLessThan(String value) {
            addCriterion("outcome <", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeLessThanOrEqualTo(String value) {
            addCriterion("outcome <=", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeLike(String value) {
            addCriterion("outcome like", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeNotLike(String value) {
            addCriterion("outcome not like", value, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeIn(List<String> values) {
            addCriterion("outcome in", values, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeNotIn(List<String> values) {
            addCriterion("outcome not in", values, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeBetween(String value1, String value2) {
            addCriterion("outcome between", value1, value2, "outcome");
            return (Criteria) this;
        }

        public Criteria andOutcomeNotBetween(String value1, String value2) {
            addCriterion("outcome not between", value1, value2, "outcome");
            return (Criteria) this;
        }

        public Criteria andErrorCodeIsNull() {
            addCriterion("error_code is null");
            return (Criteria) this;
        }

        public Criteria andErrorCodeIsNotNull() {
            addCriterion("error_code is not null");
            return (Criteria) this;
        }

        public Criteria andErrorCodeEqualTo(String value) {
            addCriterion("error_code =", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeNotEqualTo(String value) {
            addCriterion("error_code <>", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeGreaterThan(String value) {
            addCriterion("error_code >", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeGreaterThanOrEqualTo(String value) {
            addCriterion("error_code >=", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeLessThan(String value) {
            addCriterion("error_code <", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeLessThanOrEqualTo(String value) {
            addCriterion("error_code <=", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeLike(String value) {
            addCriterion("error_code like", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeNotLike(String value) {
            addCriterion("error_code not like", value, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeIn(List<String> values) {
            addCriterion("error_code in", values, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeNotIn(List<String> values) {
            addCriterion("error_code not in", values, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeBetween(String value1, String value2) {
            addCriterion("error_code between", value1, value2, "errorCode");
            return (Criteria) this;
        }

        public Criteria andErrorCodeNotBetween(String value1, String value2) {
            addCriterion("error_code not between", value1, value2, "errorCode");
            return (Criteria) this;
        }

        public Criteria andDurationMsIsNull() {
            addCriterion("duration_ms is null");
            return (Criteria) this;
        }

        public Criteria andDurationMsIsNotNull() {
            addCriterion("duration_ms is not null");
            return (Criteria) this;
        }

        public Criteria andDurationMsEqualTo(Long value) {
            addCriterion("duration_ms =", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsNotEqualTo(Long value) {
            addCriterion("duration_ms <>", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsGreaterThan(Long value) {
            addCriterion("duration_ms >", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsGreaterThanOrEqualTo(Long value) {
            addCriterion("duration_ms >=", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsLessThan(Long value) {
            addCriterion("duration_ms <", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsLessThanOrEqualTo(Long value) {
            addCriterion("duration_ms <=", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsIn(List<Long> values) {
            addCriterion("duration_ms in", values, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsNotIn(List<Long> values) {
            addCriterion("duration_ms not in", values, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsBetween(Long value1, Long value2) {
            addCriterion("duration_ms between", value1, value2, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsNotBetween(Long value1, Long value2) {
            addCriterion("duration_ms not between", value1, value2, "durationMs");
            return (Criteria) this;
        }

        public Criteria andOccurredAtIsNull() {
            addCriterion("occurred_at is null");
            return (Criteria) this;
        }

        public Criteria andOccurredAtIsNotNull() {
            addCriterion("occurred_at is not null");
            return (Criteria) this;
        }

        public Criteria andOccurredAtEqualTo(LocalDateTime value) {
            addCriterion("occurred_at =", value, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtNotEqualTo(LocalDateTime value) {
            addCriterion("occurred_at <>", value, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtGreaterThan(LocalDateTime value) {
            addCriterion("occurred_at >", value, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("occurred_at >=", value, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtLessThan(LocalDateTime value) {
            addCriterion("occurred_at <", value, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("occurred_at <=", value, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtIn(List<LocalDateTime> values) {
            addCriterion("occurred_at in", values, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtNotIn(List<LocalDateTime> values) {
            addCriterion("occurred_at not in", values, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("occurred_at between", value1, value2, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andOccurredAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("occurred_at not between", value1, value2, "occurredAt");
            return (Criteria) this;
        }

        public Criteria andRequestIdIsNull() {
            addCriterion("request_id is null");
            return (Criteria) this;
        }

        public Criteria andRequestIdIsNotNull() {
            addCriterion("request_id is not null");
            return (Criteria) this;
        }

        public Criteria andRequestIdEqualTo(String value) {
            addCriterion("request_id =", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdNotEqualTo(String value) {
            addCriterion("request_id <>", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdGreaterThan(String value) {
            addCriterion("request_id >", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdGreaterThanOrEqualTo(String value) {
            addCriterion("request_id >=", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdLessThan(String value) {
            addCriterion("request_id <", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdLessThanOrEqualTo(String value) {
            addCriterion("request_id <=", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdLike(String value) {
            addCriterion("request_id like", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdNotLike(String value) {
            addCriterion("request_id not like", value, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdIn(List<String> values) {
            addCriterion("request_id in", values, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdNotIn(List<String> values) {
            addCriterion("request_id not in", values, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdBetween(String value1, String value2) {
            addCriterion("request_id between", value1, value2, "requestId");
            return (Criteria) this;
        }

        public Criteria andRequestIdNotBetween(String value1, String value2) {
            addCriterion("request_id not between", value1, value2, "requestId");
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