// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesExecutionPathExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesExecutionPathExample() {
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

        public Criteria andPathStatusIsNull() {
            addCriterion("path_status is null");
            return (Criteria) this;
        }

        public Criteria andPathStatusIsNotNull() {
            addCriterion("path_status is not null");
            return (Criteria) this;
        }

        public Criteria andPathStatusEqualTo(String value) {
            addCriterion("path_status =", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusNotEqualTo(String value) {
            addCriterion("path_status <>", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusGreaterThan(String value) {
            addCriterion("path_status >", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusGreaterThanOrEqualTo(String value) {
            addCriterion("path_status >=", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusLessThan(String value) {
            addCriterion("path_status <", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusLessThanOrEqualTo(String value) {
            addCriterion("path_status <=", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusLike(String value) {
            addCriterion("path_status like", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusNotLike(String value) {
            addCriterion("path_status not like", value, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusIn(List<String> values) {
            addCriterion("path_status in", values, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusNotIn(List<String> values) {
            addCriterion("path_status not in", values, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusBetween(String value1, String value2) {
            addCriterion("path_status between", value1, value2, "pathStatus");
            return (Criteria) this;
        }

        public Criteria andPathStatusNotBetween(String value1, String value2) {
            addCriterion("path_status not between", value1, value2, "pathStatus");
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

        public Criteria andSelectedAtIsNull() {
            addCriterion("selected_at is null");
            return (Criteria) this;
        }

        public Criteria andSelectedAtIsNotNull() {
            addCriterion("selected_at is not null");
            return (Criteria) this;
        }

        public Criteria andSelectedAtEqualTo(LocalDateTime value) {
            addCriterion("selected_at =", value, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtNotEqualTo(LocalDateTime value) {
            addCriterion("selected_at <>", value, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtGreaterThan(LocalDateTime value) {
            addCriterion("selected_at >", value, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("selected_at >=", value, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtLessThan(LocalDateTime value) {
            addCriterion("selected_at <", value, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("selected_at <=", value, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtIn(List<LocalDateTime> values) {
            addCriterion("selected_at in", values, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtNotIn(List<LocalDateTime> values) {
            addCriterion("selected_at not in", values, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("selected_at between", value1, value2, "selectedAt");
            return (Criteria) this;
        }

        public Criteria andSelectedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("selected_at not between", value1, value2, "selectedAt");
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