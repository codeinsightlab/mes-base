// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesWarehouseAcceptanceExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesWarehouseAcceptanceExample() {
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

        public Criteria andReceiptIdIsNull() {
            addCriterion("receipt_id is null");
            return (Criteria) this;
        }

        public Criteria andReceiptIdIsNotNull() {
            addCriterion("receipt_id is not null");
            return (Criteria) this;
        }

        public Criteria andReceiptIdEqualTo(String value) {
            addCriterion("receipt_id =", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdNotEqualTo(String value) {
            addCriterion("receipt_id <>", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdGreaterThan(String value) {
            addCriterion("receipt_id >", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdGreaterThanOrEqualTo(String value) {
            addCriterion("receipt_id >=", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdLessThan(String value) {
            addCriterion("receipt_id <", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdLessThanOrEqualTo(String value) {
            addCriterion("receipt_id <=", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdLike(String value) {
            addCriterion("receipt_id like", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdNotLike(String value) {
            addCriterion("receipt_id not like", value, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdIn(List<String> values) {
            addCriterion("receipt_id in", values, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdNotIn(List<String> values) {
            addCriterion("receipt_id not in", values, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdBetween(String value1, String value2) {
            addCriterion("receipt_id between", value1, value2, "receiptId");
            return (Criteria) this;
        }

        public Criteria andReceiptIdNotBetween(String value1, String value2) {
            addCriterion("receipt_id not between", value1, value2, "receiptId");
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

        public Criteria andStockIdIsNull() {
            addCriterion("stock_id is null");
            return (Criteria) this;
        }

        public Criteria andStockIdIsNotNull() {
            addCriterion("stock_id is not null");
            return (Criteria) this;
        }

        public Criteria andStockIdEqualTo(Long value) {
            addCriterion("stock_id =", value, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdNotEqualTo(Long value) {
            addCriterion("stock_id <>", value, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdGreaterThan(Long value) {
            addCriterion("stock_id >", value, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdGreaterThanOrEqualTo(Long value) {
            addCriterion("stock_id >=", value, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdLessThan(Long value) {
            addCriterion("stock_id <", value, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdLessThanOrEqualTo(Long value) {
            addCriterion("stock_id <=", value, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdIn(List<Long> values) {
            addCriterion("stock_id in", values, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdNotIn(List<Long> values) {
            addCriterion("stock_id not in", values, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdBetween(Long value1, Long value2) {
            addCriterion("stock_id between", value1, value2, "stockId");
            return (Criteria) this;
        }

        public Criteria andStockIdNotBetween(Long value1, Long value2) {
            addCriterion("stock_id not between", value1, value2, "stockId");
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

        public Criteria andReferenceIdIsNull() {
            addCriterion("reference_id is null");
            return (Criteria) this;
        }

        public Criteria andReferenceIdIsNotNull() {
            addCriterion("reference_id is not null");
            return (Criteria) this;
        }

        public Criteria andReferenceIdEqualTo(String value) {
            addCriterion("reference_id =", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdNotEqualTo(String value) {
            addCriterion("reference_id <>", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdGreaterThan(String value) {
            addCriterion("reference_id >", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdGreaterThanOrEqualTo(String value) {
            addCriterion("reference_id >=", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdLessThan(String value) {
            addCriterion("reference_id <", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdLessThanOrEqualTo(String value) {
            addCriterion("reference_id <=", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdLike(String value) {
            addCriterion("reference_id like", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdNotLike(String value) {
            addCriterion("reference_id not like", value, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdIn(List<String> values) {
            addCriterion("reference_id in", values, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdNotIn(List<String> values) {
            addCriterion("reference_id not in", values, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdBetween(String value1, String value2) {
            addCriterion("reference_id between", value1, value2, "referenceId");
            return (Criteria) this;
        }

        public Criteria andReferenceIdNotBetween(String value1, String value2) {
            addCriterion("reference_id not between", value1, value2, "referenceId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdIsNull() {
            addCriterion("stock_action_id is null");
            return (Criteria) this;
        }

        public Criteria andStockActionIdIsNotNull() {
            addCriterion("stock_action_id is not null");
            return (Criteria) this;
        }

        public Criteria andStockActionIdEqualTo(String value) {
            addCriterion("stock_action_id =", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdNotEqualTo(String value) {
            addCriterion("stock_action_id <>", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdGreaterThan(String value) {
            addCriterion("stock_action_id >", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdGreaterThanOrEqualTo(String value) {
            addCriterion("stock_action_id >=", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdLessThan(String value) {
            addCriterion("stock_action_id <", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdLessThanOrEqualTo(String value) {
            addCriterion("stock_action_id <=", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdLike(String value) {
            addCriterion("stock_action_id like", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdNotLike(String value) {
            addCriterion("stock_action_id not like", value, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdIn(List<String> values) {
            addCriterion("stock_action_id in", values, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdNotIn(List<String> values) {
            addCriterion("stock_action_id not in", values, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdBetween(String value1, String value2) {
            addCriterion("stock_action_id between", value1, value2, "stockActionId");
            return (Criteria) this;
        }

        public Criteria andStockActionIdNotBetween(String value1, String value2) {
            addCriterion("stock_action_id not between", value1, value2, "stockActionId");
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