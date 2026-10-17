// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesExecutionCompletionExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesExecutionCompletionExample() {
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

        public Criteria andEffectiveQtyIsNull() {
            addCriterion("effective_qty is null");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyIsNotNull() {
            addCriterion("effective_qty is not null");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyEqualTo(BigDecimal value) {
            addCriterion("effective_qty =", value, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyNotEqualTo(BigDecimal value) {
            addCriterion("effective_qty <>", value, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyGreaterThan(BigDecimal value) {
            addCriterion("effective_qty >", value, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("effective_qty >=", value, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyLessThan(BigDecimal value) {
            addCriterion("effective_qty <", value, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("effective_qty <=", value, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyIn(List<BigDecimal> values) {
            addCriterion("effective_qty in", values, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyNotIn(List<BigDecimal> values) {
            addCriterion("effective_qty not in", values, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("effective_qty between", value1, value2, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andEffectiveQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("effective_qty not between", value1, value2, "effectiveQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyIsNull() {
            addCriterion("released_qty is null");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyIsNotNull() {
            addCriterion("released_qty is not null");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyEqualTo(BigDecimal value) {
            addCriterion("released_qty =", value, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyNotEqualTo(BigDecimal value) {
            addCriterion("released_qty <>", value, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyGreaterThan(BigDecimal value) {
            addCriterion("released_qty >", value, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("released_qty >=", value, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyLessThan(BigDecimal value) {
            addCriterion("released_qty <", value, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("released_qty <=", value, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyIn(List<BigDecimal> values) {
            addCriterion("released_qty in", values, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyNotIn(List<BigDecimal> values) {
            addCriterion("released_qty not in", values, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("released_qty between", value1, value2, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReleasedQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("released_qty not between", value1, value2, "releasedQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyIsNull() {
            addCriterion("receipt_qty is null");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyIsNotNull() {
            addCriterion("receipt_qty is not null");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyEqualTo(BigDecimal value) {
            addCriterion("receipt_qty =", value, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyNotEqualTo(BigDecimal value) {
            addCriterion("receipt_qty <>", value, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyGreaterThan(BigDecimal value) {
            addCriterion("receipt_qty >", value, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("receipt_qty >=", value, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyLessThan(BigDecimal value) {
            addCriterion("receipt_qty <", value, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("receipt_qty <=", value, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyIn(List<BigDecimal> values) {
            addCriterion("receipt_qty in", values, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyNotIn(List<BigDecimal> values) {
            addCriterion("receipt_qty not in", values, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("receipt_qty between", value1, value2, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andReceiptQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("receipt_qty not between", value1, value2, "receiptQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyIsNull() {
            addCriterion("accepted_qty is null");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyIsNotNull() {
            addCriterion("accepted_qty is not null");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyEqualTo(BigDecimal value) {
            addCriterion("accepted_qty =", value, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyNotEqualTo(BigDecimal value) {
            addCriterion("accepted_qty <>", value, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyGreaterThan(BigDecimal value) {
            addCriterion("accepted_qty >", value, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("accepted_qty >=", value, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyLessThan(BigDecimal value) {
            addCriterion("accepted_qty <", value, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("accepted_qty <=", value, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyIn(List<BigDecimal> values) {
            addCriterion("accepted_qty in", values, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyNotIn(List<BigDecimal> values) {
            addCriterion("accepted_qty not in", values, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("accepted_qty between", value1, value2, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andAcceptedQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("accepted_qty not between", value1, value2, "acceptedQty");
            return (Criteria) this;
        }

        public Criteria andBasisHashIsNull() {
            addCriterion("basis_hash is null");
            return (Criteria) this;
        }

        public Criteria andBasisHashIsNotNull() {
            addCriterion("basis_hash is not null");
            return (Criteria) this;
        }

        public Criteria andBasisHashEqualTo(String value) {
            addCriterion("basis_hash =", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashNotEqualTo(String value) {
            addCriterion("basis_hash <>", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashGreaterThan(String value) {
            addCriterion("basis_hash >", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashGreaterThanOrEqualTo(String value) {
            addCriterion("basis_hash >=", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashLessThan(String value) {
            addCriterion("basis_hash <", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashLessThanOrEqualTo(String value) {
            addCriterion("basis_hash <=", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashLike(String value) {
            addCriterion("basis_hash like", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashNotLike(String value) {
            addCriterion("basis_hash not like", value, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashIn(List<String> values) {
            addCriterion("basis_hash in", values, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashNotIn(List<String> values) {
            addCriterion("basis_hash not in", values, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashBetween(String value1, String value2) {
            addCriterion("basis_hash between", value1, value2, "basisHash");
            return (Criteria) this;
        }

        public Criteria andBasisHashNotBetween(String value1, String value2) {
            addCriterion("basis_hash not between", value1, value2, "basisHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashIsNull() {
            addCriterion("request_hash is null");
            return (Criteria) this;
        }

        public Criteria andRequestHashIsNotNull() {
            addCriterion("request_hash is not null");
            return (Criteria) this;
        }

        public Criteria andRequestHashEqualTo(String value) {
            addCriterion("request_hash =", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashNotEqualTo(String value) {
            addCriterion("request_hash <>", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashGreaterThan(String value) {
            addCriterion("request_hash >", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashGreaterThanOrEqualTo(String value) {
            addCriterion("request_hash >=", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashLessThan(String value) {
            addCriterion("request_hash <", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashLessThanOrEqualTo(String value) {
            addCriterion("request_hash <=", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashLike(String value) {
            addCriterion("request_hash like", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashNotLike(String value) {
            addCriterion("request_hash not like", value, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashIn(List<String> values) {
            addCriterion("request_hash in", values, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashNotIn(List<String> values) {
            addCriterion("request_hash not in", values, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashBetween(String value1, String value2) {
            addCriterion("request_hash between", value1, value2, "requestHash");
            return (Criteria) this;
        }

        public Criteria andRequestHashNotBetween(String value1, String value2) {
            addCriterion("request_hash not between", value1, value2, "requestHash");
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

        public Criteria andRecordedAtIsNull() {
            addCriterion("recorded_at is null");
            return (Criteria) this;
        }

        public Criteria andRecordedAtIsNotNull() {
            addCriterion("recorded_at is not null");
            return (Criteria) this;
        }

        public Criteria andRecordedAtEqualTo(LocalDateTime value) {
            addCriterion("recorded_at =", value, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtNotEqualTo(LocalDateTime value) {
            addCriterion("recorded_at <>", value, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtGreaterThan(LocalDateTime value) {
            addCriterion("recorded_at >", value, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("recorded_at >=", value, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtLessThan(LocalDateTime value) {
            addCriterion("recorded_at <", value, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("recorded_at <=", value, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtIn(List<LocalDateTime> values) {
            addCriterion("recorded_at in", values, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtNotIn(List<LocalDateTime> values) {
            addCriterion("recorded_at not in", values, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("recorded_at between", value1, value2, "recordedAt");
            return (Criteria) this;
        }

        public Criteria andRecordedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("recorded_at not between", value1, value2, "recordedAt");
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