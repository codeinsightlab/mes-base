// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesMaterialIssueInputExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesMaterialIssueInputExample() {
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

        public Criteria andDocumentIdIsNull() {
            addCriterion("document_id is null");
            return (Criteria) this;
        }

        public Criteria andDocumentIdIsNotNull() {
            addCriterion("document_id is not null");
            return (Criteria) this;
        }

        public Criteria andDocumentIdEqualTo(Long value) {
            addCriterion("document_id =", value, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdNotEqualTo(Long value) {
            addCriterion("document_id <>", value, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdGreaterThan(Long value) {
            addCriterion("document_id >", value, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdGreaterThanOrEqualTo(Long value) {
            addCriterion("document_id >=", value, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdLessThan(Long value) {
            addCriterion("document_id <", value, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdLessThanOrEqualTo(Long value) {
            addCriterion("document_id <=", value, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdIn(List<Long> values) {
            addCriterion("document_id in", values, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdNotIn(List<Long> values) {
            addCriterion("document_id not in", values, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdBetween(Long value1, Long value2) {
            addCriterion("document_id between", value1, value2, "documentId");
            return (Criteria) this;
        }

        public Criteria andDocumentIdNotBetween(Long value1, Long value2) {
            addCriterion("document_id not between", value1, value2, "documentId");
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

        public Criteria andInputKindIsNull() {
            addCriterion("input_kind is null");
            return (Criteria) this;
        }

        public Criteria andInputKindIsNotNull() {
            addCriterion("input_kind is not null");
            return (Criteria) this;
        }

        public Criteria andInputKindEqualTo(String value) {
            addCriterion("input_kind =", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindNotEqualTo(String value) {
            addCriterion("input_kind <>", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindGreaterThan(String value) {
            addCriterion("input_kind >", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindGreaterThanOrEqualTo(String value) {
            addCriterion("input_kind >=", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindLessThan(String value) {
            addCriterion("input_kind <", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindLessThanOrEqualTo(String value) {
            addCriterion("input_kind <=", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindLike(String value) {
            addCriterion("input_kind like", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindNotLike(String value) {
            addCriterion("input_kind not like", value, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindIn(List<String> values) {
            addCriterion("input_kind in", values, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindNotIn(List<String> values) {
            addCriterion("input_kind not in", values, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindBetween(String value1, String value2) {
            addCriterion("input_kind between", value1, value2, "inputKind");
            return (Criteria) this;
        }

        public Criteria andInputKindNotBetween(String value1, String value2) {
            addCriterion("input_kind not between", value1, value2, "inputKind");
            return (Criteria) this;
        }

        public Criteria andOutputQtyIsNull() {
            addCriterion("output_qty is null");
            return (Criteria) this;
        }

        public Criteria andOutputQtyIsNotNull() {
            addCriterion("output_qty is not null");
            return (Criteria) this;
        }

        public Criteria andOutputQtyEqualTo(BigDecimal value) {
            addCriterion("output_qty =", value, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyNotEqualTo(BigDecimal value) {
            addCriterion("output_qty <>", value, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyGreaterThan(BigDecimal value) {
            addCriterion("output_qty >", value, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("output_qty >=", value, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyLessThan(BigDecimal value) {
            addCriterion("output_qty <", value, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("output_qty <=", value, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyIn(List<BigDecimal> values) {
            addCriterion("output_qty in", values, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyNotIn(List<BigDecimal> values) {
            addCriterion("output_qty not in", values, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("output_qty between", value1, value2, "outputQty");
            return (Criteria) this;
        }

        public Criteria andOutputQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("output_qty not between", value1, value2, "outputQty");
            return (Criteria) this;
        }

        public Criteria andSourceModeIsNull() {
            addCriterion("source_mode is null");
            return (Criteria) this;
        }

        public Criteria andSourceModeIsNotNull() {
            addCriterion("source_mode is not null");
            return (Criteria) this;
        }

        public Criteria andSourceModeEqualTo(String value) {
            addCriterion("source_mode =", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeNotEqualTo(String value) {
            addCriterion("source_mode <>", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeGreaterThan(String value) {
            addCriterion("source_mode >", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeGreaterThanOrEqualTo(String value) {
            addCriterion("source_mode >=", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeLessThan(String value) {
            addCriterion("source_mode <", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeLessThanOrEqualTo(String value) {
            addCriterion("source_mode <=", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeLike(String value) {
            addCriterion("source_mode like", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeNotLike(String value) {
            addCriterion("source_mode not like", value, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeIn(List<String> values) {
            addCriterion("source_mode in", values, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeNotIn(List<String> values) {
            addCriterion("source_mode not in", values, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeBetween(String value1, String value2) {
            addCriterion("source_mode between", value1, value2, "sourceMode");
            return (Criteria) this;
        }

        public Criteria andSourceModeNotBetween(String value1, String value2) {
            addCriterion("source_mode not between", value1, value2, "sourceMode");
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

        public Criteria andConfirmedAtIsNull() {
            addCriterion("confirmed_at is null");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtIsNotNull() {
            addCriterion("confirmed_at is not null");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtEqualTo(LocalDateTime value) {
            addCriterion("confirmed_at =", value, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtNotEqualTo(LocalDateTime value) {
            addCriterion("confirmed_at <>", value, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtGreaterThan(LocalDateTime value) {
            addCriterion("confirmed_at >", value, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("confirmed_at >=", value, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtLessThan(LocalDateTime value) {
            addCriterion("confirmed_at <", value, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("confirmed_at <=", value, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtIn(List<LocalDateTime> values) {
            addCriterion("confirmed_at in", values, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtNotIn(List<LocalDateTime> values) {
            addCriterion("confirmed_at not in", values, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("confirmed_at between", value1, value2, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andConfirmedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("confirmed_at not between", value1, value2, "confirmedAt");
            return (Criteria) this;
        }

        public Criteria andStockAppliedIsNull() {
            addCriterion("stock_applied is null");
            return (Criteria) this;
        }

        public Criteria andStockAppliedIsNotNull() {
            addCriterion("stock_applied is not null");
            return (Criteria) this;
        }

        public Criteria andStockAppliedEqualTo(Boolean value) {
            addCriterion("stock_applied =", value, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedNotEqualTo(Boolean value) {
            addCriterion("stock_applied <>", value, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedGreaterThan(Boolean value) {
            addCriterion("stock_applied >", value, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedGreaterThanOrEqualTo(Boolean value) {
            addCriterion("stock_applied >=", value, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedLessThan(Boolean value) {
            addCriterion("stock_applied <", value, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedLessThanOrEqualTo(Boolean value) {
            addCriterion("stock_applied <=", value, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedIn(List<Boolean> values) {
            addCriterion("stock_applied in", values, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedNotIn(List<Boolean> values) {
            addCriterion("stock_applied not in", values, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedBetween(Boolean value1, Boolean value2) {
            addCriterion("stock_applied between", value1, value2, "stockApplied");
            return (Criteria) this;
        }

        public Criteria andStockAppliedNotBetween(Boolean value1, Boolean value2) {
            addCriterion("stock_applied not between", value1, value2, "stockApplied");
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
        public Criteria andBusinessOccurredAtIsNull() {
            addCriterion("business_occurred_at is null");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtIsNotNull() {
            addCriterion("business_occurred_at is not null");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtEqualTo(LocalDateTime value) {
            addCriterion("business_occurred_at =", value, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtNotEqualTo(LocalDateTime value) {
            addCriterion("business_occurred_at <>", value, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtGreaterThan(LocalDateTime value) {
            addCriterion("business_occurred_at >", value, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("business_occurred_at >=", value, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtLessThan(LocalDateTime value) {
            addCriterion("business_occurred_at <", value, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("business_occurred_at <=", value, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtIn(List<LocalDateTime> values) {
            addCriterion("business_occurred_at in", values, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtNotIn(List<LocalDateTime> values) {
            addCriterion("business_occurred_at not in", values, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("business_occurred_at between", value1, value2, "businessOccurredAt");
            return (Criteria) this;
        }

        public Criteria andBusinessOccurredAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("business_occurred_at not between", value1, value2, "businessOccurredAt");
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