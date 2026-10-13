// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.util.ArrayList;
import java.util.List;

public class BatchRouteNodeExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public BatchRouteNodeExample() {
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

        public Criteria andBatchIdIsNull() {
            addCriterion("batch_id is null");
            return (Criteria) this;
        }

        public Criteria andBatchIdIsNotNull() {
            addCriterion("batch_id is not null");
            return (Criteria) this;
        }

        public Criteria andBatchIdEqualTo(Long value) {
            addCriterion("batch_id =", value, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdNotEqualTo(Long value) {
            addCriterion("batch_id <>", value, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdGreaterThan(Long value) {
            addCriterion("batch_id >", value, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdGreaterThanOrEqualTo(Long value) {
            addCriterion("batch_id >=", value, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdLessThan(Long value) {
            addCriterion("batch_id <", value, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdLessThanOrEqualTo(Long value) {
            addCriterion("batch_id <=", value, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdIn(List<Long> values) {
            addCriterion("batch_id in", values, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdNotIn(List<Long> values) {
            addCriterion("batch_id not in", values, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdBetween(Long value1, Long value2) {
            addCriterion("batch_id between", value1, value2, "batchId");
            return (Criteria) this;
        }

        public Criteria andBatchIdNotBetween(Long value1, Long value2) {
            addCriterion("batch_id not between", value1, value2, "batchId");
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

        public Criteria andExecutionOrderIsNull() {
            addCriterion("execution_order is null");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderIsNotNull() {
            addCriterion("execution_order is not null");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderEqualTo(Integer value) {
            addCriterion("execution_order =", value, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderNotEqualTo(Integer value) {
            addCriterion("execution_order <>", value, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderGreaterThan(Integer value) {
            addCriterion("execution_order >", value, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderGreaterThanOrEqualTo(Integer value) {
            addCriterion("execution_order >=", value, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderLessThan(Integer value) {
            addCriterion("execution_order <", value, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderLessThanOrEqualTo(Integer value) {
            addCriterion("execution_order <=", value, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderIn(List<Integer> values) {
            addCriterion("execution_order in", values, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderNotIn(List<Integer> values) {
            addCriterion("execution_order not in", values, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderBetween(Integer value1, Integer value2) {
            addCriterion("execution_order between", value1, value2, "executionOrder");
            return (Criteria) this;
        }

        public Criteria andExecutionOrderNotBetween(Integer value1, Integer value2) {
            addCriterion("execution_order not between", value1, value2, "executionOrder");
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