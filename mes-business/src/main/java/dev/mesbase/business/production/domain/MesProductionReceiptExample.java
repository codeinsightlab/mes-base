// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesProductionReceiptExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesProductionReceiptExample() {
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

        public Criteria andReceiptNoIsNull() {
            addCriterion("receipt_no is null");
            return (Criteria) this;
        }

        public Criteria andReceiptNoIsNotNull() {
            addCriterion("receipt_no is not null");
            return (Criteria) this;
        }

        public Criteria andReceiptNoEqualTo(String value) {
            addCriterion("receipt_no =", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoNotEqualTo(String value) {
            addCriterion("receipt_no <>", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoGreaterThan(String value) {
            addCriterion("receipt_no >", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoGreaterThanOrEqualTo(String value) {
            addCriterion("receipt_no >=", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoLessThan(String value) {
            addCriterion("receipt_no <", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoLessThanOrEqualTo(String value) {
            addCriterion("receipt_no <=", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoLike(String value) {
            addCriterion("receipt_no like", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoNotLike(String value) {
            addCriterion("receipt_no not like", value, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoIn(List<String> values) {
            addCriterion("receipt_no in", values, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoNotIn(List<String> values) {
            addCriterion("receipt_no not in", values, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoBetween(String value1, String value2) {
            addCriterion("receipt_no between", value1, value2, "receiptNo");
            return (Criteria) this;
        }

        public Criteria andReceiptNoNotBetween(String value1, String value2) {
            addCriterion("receipt_no not between", value1, value2, "receiptNo");
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

        public Criteria andProductLotCodeIsNull() {
            addCriterion("product_lot_code is null");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeIsNotNull() {
            addCriterion("product_lot_code is not null");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeEqualTo(String value) {
            addCriterion("product_lot_code =", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeNotEqualTo(String value) {
            addCriterion("product_lot_code <>", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeGreaterThan(String value) {
            addCriterion("product_lot_code >", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeGreaterThanOrEqualTo(String value) {
            addCriterion("product_lot_code >=", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeLessThan(String value) {
            addCriterion("product_lot_code <", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeLessThanOrEqualTo(String value) {
            addCriterion("product_lot_code <=", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeLike(String value) {
            addCriterion("product_lot_code like", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeNotLike(String value) {
            addCriterion("product_lot_code not like", value, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeIn(List<String> values) {
            addCriterion("product_lot_code in", values, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeNotIn(List<String> values) {
            addCriterion("product_lot_code not in", values, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeBetween(String value1, String value2) {
            addCriterion("product_lot_code between", value1, value2, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andProductLotCodeNotBetween(String value1, String value2) {
            addCriterion("product_lot_code not between", value1, value2, "productLotCode");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdIsNull() {
            addCriterion("warehouse_id is null");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdIsNotNull() {
            addCriterion("warehouse_id is not null");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdEqualTo(Long value) {
            addCriterion("warehouse_id =", value, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdNotEqualTo(Long value) {
            addCriterion("warehouse_id <>", value, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdGreaterThan(Long value) {
            addCriterion("warehouse_id >", value, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdGreaterThanOrEqualTo(Long value) {
            addCriterion("warehouse_id >=", value, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdLessThan(Long value) {
            addCriterion("warehouse_id <", value, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdLessThanOrEqualTo(Long value) {
            addCriterion("warehouse_id <=", value, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdIn(List<Long> values) {
            addCriterion("warehouse_id in", values, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdNotIn(List<Long> values) {
            addCriterion("warehouse_id not in", values, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdBetween(Long value1, Long value2) {
            addCriterion("warehouse_id between", value1, value2, "warehouseId");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdNotBetween(Long value1, Long value2) {
            addCriterion("warehouse_id not between", value1, value2, "warehouseId");
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

        public Criteria andReferenceReceiptIdIsNull() {
            addCriterion("reference_receipt_id is null");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdIsNotNull() {
            addCriterion("reference_receipt_id is not null");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdEqualTo(String value) {
            addCriterion("reference_receipt_id =", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdNotEqualTo(String value) {
            addCriterion("reference_receipt_id <>", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdGreaterThan(String value) {
            addCriterion("reference_receipt_id >", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdGreaterThanOrEqualTo(String value) {
            addCriterion("reference_receipt_id >=", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdLessThan(String value) {
            addCriterion("reference_receipt_id <", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdLessThanOrEqualTo(String value) {
            addCriterion("reference_receipt_id <=", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdLike(String value) {
            addCriterion("reference_receipt_id like", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdNotLike(String value) {
            addCriterion("reference_receipt_id not like", value, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdIn(List<String> values) {
            addCriterion("reference_receipt_id in", values, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdNotIn(List<String> values) {
            addCriterion("reference_receipt_id not in", values, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdBetween(String value1, String value2) {
            addCriterion("reference_receipt_id between", value1, value2, "referenceReceiptId");
            return (Criteria) this;
        }

        public Criteria andReferenceReceiptIdNotBetween(String value1, String value2) {
            addCriterion("reference_receipt_id not between", value1, value2, "referenceReceiptId");
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