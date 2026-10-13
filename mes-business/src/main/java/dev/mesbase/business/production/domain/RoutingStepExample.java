// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.util.ArrayList;
import java.util.List;

public class RoutingStepExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public RoutingStepExample() {
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

        public Criteria andRoutingIdIsNull() {
            addCriterion("routing_id is null");
            return (Criteria) this;
        }

        public Criteria andRoutingIdIsNotNull() {
            addCriterion("routing_id is not null");
            return (Criteria) this;
        }

        public Criteria andRoutingIdEqualTo(Long value) {
            addCriterion("routing_id =", value, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdNotEqualTo(Long value) {
            addCriterion("routing_id <>", value, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdGreaterThan(Long value) {
            addCriterion("routing_id >", value, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdGreaterThanOrEqualTo(Long value) {
            addCriterion("routing_id >=", value, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdLessThan(Long value) {
            addCriterion("routing_id <", value, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdLessThanOrEqualTo(Long value) {
            addCriterion("routing_id <=", value, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdIn(List<Long> values) {
            addCriterion("routing_id in", values, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdNotIn(List<Long> values) {
            addCriterion("routing_id not in", values, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdBetween(Long value1, Long value2) {
            addCriterion("routing_id between", value1, value2, "routingId");
            return (Criteria) this;
        }

        public Criteria andRoutingIdNotBetween(Long value1, Long value2) {
            addCriterion("routing_id not between", value1, value2, "routingId");
            return (Criteria) this;
        }

        public Criteria andSequenceIsNull() {
            addCriterion("sequence is null");
            return (Criteria) this;
        }

        public Criteria andSequenceIsNotNull() {
            addCriterion("sequence is not null");
            return (Criteria) this;
        }

        public Criteria andSequenceEqualTo(Integer value) {
            addCriterion("sequence =", value, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceNotEqualTo(Integer value) {
            addCriterion("sequence <>", value, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceGreaterThan(Integer value) {
            addCriterion("sequence >", value, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceGreaterThanOrEqualTo(Integer value) {
            addCriterion("sequence >=", value, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceLessThan(Integer value) {
            addCriterion("sequence <", value, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceLessThanOrEqualTo(Integer value) {
            addCriterion("sequence <=", value, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceIn(List<Integer> values) {
            addCriterion("sequence in", values, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceNotIn(List<Integer> values) {
            addCriterion("sequence not in", values, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceBetween(Integer value1, Integer value2) {
            addCriterion("sequence between", value1, value2, "sequence");
            return (Criteria) this;
        }

        public Criteria andSequenceNotBetween(Integer value1, Integer value2) {
            addCriterion("sequence not between", value1, value2, "sequence");
            return (Criteria) this;
        }

        public Criteria andOperationIdIsNull() {
            addCriterion("operation_id is null");
            return (Criteria) this;
        }

        public Criteria andOperationIdIsNotNull() {
            addCriterion("operation_id is not null");
            return (Criteria) this;
        }

        public Criteria andOperationIdEqualTo(Long value) {
            addCriterion("operation_id =", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotEqualTo(Long value) {
            addCriterion("operation_id <>", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdGreaterThan(Long value) {
            addCriterion("operation_id >", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdGreaterThanOrEqualTo(Long value) {
            addCriterion("operation_id >=", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdLessThan(Long value) {
            addCriterion("operation_id <", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdLessThanOrEqualTo(Long value) {
            addCriterion("operation_id <=", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdIn(List<Long> values) {
            addCriterion("operation_id in", values, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotIn(List<Long> values) {
            addCriterion("operation_id not in", values, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdBetween(Long value1, Long value2) {
            addCriterion("operation_id between", value1, value2, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotBetween(Long value1, Long value2) {
            addCriterion("operation_id not between", value1, value2, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationCodeIsNull() {
            addCriterion("operation_code is null");
            return (Criteria) this;
        }

        public Criteria andOperationCodeIsNotNull() {
            addCriterion("operation_code is not null");
            return (Criteria) this;
        }

        public Criteria andOperationCodeEqualTo(String value) {
            addCriterion("operation_code =", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeNotEqualTo(String value) {
            addCriterion("operation_code <>", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeGreaterThan(String value) {
            addCriterion("operation_code >", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeGreaterThanOrEqualTo(String value) {
            addCriterion("operation_code >=", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeLessThan(String value) {
            addCriterion("operation_code <", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeLessThanOrEqualTo(String value) {
            addCriterion("operation_code <=", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeLike(String value) {
            addCriterion("operation_code like", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeNotLike(String value) {
            addCriterion("operation_code not like", value, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeIn(List<String> values) {
            addCriterion("operation_code in", values, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeNotIn(List<String> values) {
            addCriterion("operation_code not in", values, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeBetween(String value1, String value2) {
            addCriterion("operation_code between", value1, value2, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationCodeNotBetween(String value1, String value2) {
            addCriterion("operation_code not between", value1, value2, "operationCode");
            return (Criteria) this;
        }

        public Criteria andOperationNameIsNull() {
            addCriterion("operation_name is null");
            return (Criteria) this;
        }

        public Criteria andOperationNameIsNotNull() {
            addCriterion("operation_name is not null");
            return (Criteria) this;
        }

        public Criteria andOperationNameEqualTo(String value) {
            addCriterion("operation_name =", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameNotEqualTo(String value) {
            addCriterion("operation_name <>", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameGreaterThan(String value) {
            addCriterion("operation_name >", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameGreaterThanOrEqualTo(String value) {
            addCriterion("operation_name >=", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameLessThan(String value) {
            addCriterion("operation_name <", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameLessThanOrEqualTo(String value) {
            addCriterion("operation_name <=", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameLike(String value) {
            addCriterion("operation_name like", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameNotLike(String value) {
            addCriterion("operation_name not like", value, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameIn(List<String> values) {
            addCriterion("operation_name in", values, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameNotIn(List<String> values) {
            addCriterion("operation_name not in", values, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameBetween(String value1, String value2) {
            addCriterion("operation_name between", value1, value2, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationNameNotBetween(String value1, String value2) {
            addCriterion("operation_name not between", value1, value2, "operationName");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionIsNull() {
            addCriterion("operation_description is null");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionIsNotNull() {
            addCriterion("operation_description is not null");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionEqualTo(String value) {
            addCriterion("operation_description =", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionNotEqualTo(String value) {
            addCriterion("operation_description <>", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionGreaterThan(String value) {
            addCriterion("operation_description >", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionGreaterThanOrEqualTo(String value) {
            addCriterion("operation_description >=", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionLessThan(String value) {
            addCriterion("operation_description <", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionLessThanOrEqualTo(String value) {
            addCriterion("operation_description <=", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionLike(String value) {
            addCriterion("operation_description like", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionNotLike(String value) {
            addCriterion("operation_description not like", value, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionIn(List<String> values) {
            addCriterion("operation_description in", values, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionNotIn(List<String> values) {
            addCriterion("operation_description not in", values, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionBetween(String value1, String value2) {
            addCriterion("operation_description between", value1, value2, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andOperationDescriptionNotBetween(String value1, String value2) {
            addCriterion("operation_description not between", value1, value2, "operationDescription");
            return (Criteria) this;
        }

        public Criteria andIsKeyIsNull() {
            addCriterion("is_key is null");
            return (Criteria) this;
        }

        public Criteria andIsKeyIsNotNull() {
            addCriterion("is_key is not null");
            return (Criteria) this;
        }

        public Criteria andIsKeyEqualTo(Boolean value) {
            addCriterion("is_key =", value, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyNotEqualTo(Boolean value) {
            addCriterion("is_key <>", value, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyGreaterThan(Boolean value) {
            addCriterion("is_key >", value, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyGreaterThanOrEqualTo(Boolean value) {
            addCriterion("is_key >=", value, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyLessThan(Boolean value) {
            addCriterion("is_key <", value, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyLessThanOrEqualTo(Boolean value) {
            addCriterion("is_key <=", value, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyIn(List<Boolean> values) {
            addCriterion("is_key in", values, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyNotIn(List<Boolean> values) {
            addCriterion("is_key not in", values, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyBetween(Boolean value1, Boolean value2) {
            addCriterion("is_key between", value1, value2, "isKey");
            return (Criteria) this;
        }

        public Criteria andIsKeyNotBetween(Boolean value1, Boolean value2) {
            addCriterion("is_key not between", value1, value2, "isKey");
            return (Criteria) this;
        }

        public Criteria andDescriptionIsNull() {
            addCriterion("description is null");
            return (Criteria) this;
        }

        public Criteria andDescriptionIsNotNull() {
            addCriterion("description is not null");
            return (Criteria) this;
        }

        public Criteria andDescriptionEqualTo(String value) {
            addCriterion("description =", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionNotEqualTo(String value) {
            addCriterion("description <>", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionGreaterThan(String value) {
            addCriterion("description >", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionGreaterThanOrEqualTo(String value) {
            addCriterion("description >=", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionLessThan(String value) {
            addCriterion("description <", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionLessThanOrEqualTo(String value) {
            addCriterion("description <=", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionLike(String value) {
            addCriterion("description like", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionNotLike(String value) {
            addCriterion("description not like", value, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionIn(List<String> values) {
            addCriterion("description in", values, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionNotIn(List<String> values) {
            addCriterion("description not in", values, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionBetween(String value1, String value2) {
            addCriterion("description between", value1, value2, "description");
            return (Criteria) this;
        }

        public Criteria andDescriptionNotBetween(String value1, String value2) {
            addCriterion("description not between", value1, value2, "description");
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