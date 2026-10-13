// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.basic.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SafetyStockExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public SafetyStockExample() {
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

        public Criteria andMaterialIdIsNull() {
            addCriterion("material_id is null");
            return (Criteria) this;
        }

        public Criteria andMaterialIdIsNotNull() {
            addCriterion("material_id is not null");
            return (Criteria) this;
        }

        public Criteria andMaterialIdEqualTo(Long value) {
            addCriterion("material_id =", value, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdNotEqualTo(Long value) {
            addCriterion("material_id <>", value, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdGreaterThan(Long value) {
            addCriterion("material_id >", value, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdGreaterThanOrEqualTo(Long value) {
            addCriterion("material_id >=", value, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdLessThan(Long value) {
            addCriterion("material_id <", value, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdLessThanOrEqualTo(Long value) {
            addCriterion("material_id <=", value, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdIn(List<Long> values) {
            addCriterion("material_id in", values, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdNotIn(List<Long> values) {
            addCriterion("material_id not in", values, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdBetween(Long value1, Long value2) {
            addCriterion("material_id between", value1, value2, "materialId");
            return (Criteria) this;
        }

        public Criteria andMaterialIdNotBetween(Long value1, Long value2) {
            addCriterion("material_id not between", value1, value2, "materialId");
            return (Criteria) this;
        }

        public Criteria andMinQtyIsNull() {
            addCriterion("min_qty is null");
            return (Criteria) this;
        }

        public Criteria andMinQtyIsNotNull() {
            addCriterion("min_qty is not null");
            return (Criteria) this;
        }

        public Criteria andMinQtyEqualTo(BigDecimal value) {
            addCriterion("min_qty =", value, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyNotEqualTo(BigDecimal value) {
            addCriterion("min_qty <>", value, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyGreaterThan(BigDecimal value) {
            addCriterion("min_qty >", value, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("min_qty >=", value, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyLessThan(BigDecimal value) {
            addCriterion("min_qty <", value, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("min_qty <=", value, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyIn(List<BigDecimal> values) {
            addCriterion("min_qty in", values, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyNotIn(List<BigDecimal> values) {
            addCriterion("min_qty not in", values, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("min_qty between", value1, value2, "minQty");
            return (Criteria) this;
        }

        public Criteria andMinQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("min_qty not between", value1, value2, "minQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyIsNull() {
            addCriterion("max_qty is null");
            return (Criteria) this;
        }

        public Criteria andMaxQtyIsNotNull() {
            addCriterion("max_qty is not null");
            return (Criteria) this;
        }

        public Criteria andMaxQtyEqualTo(BigDecimal value) {
            addCriterion("max_qty =", value, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyNotEqualTo(BigDecimal value) {
            addCriterion("max_qty <>", value, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyGreaterThan(BigDecimal value) {
            addCriterion("max_qty >", value, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("max_qty >=", value, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyLessThan(BigDecimal value) {
            addCriterion("max_qty <", value, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("max_qty <=", value, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyIn(List<BigDecimal> values) {
            addCriterion("max_qty in", values, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyNotIn(List<BigDecimal> values) {
            addCriterion("max_qty not in", values, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("max_qty between", value1, value2, "maxQty");
            return (Criteria) this;
        }

        public Criteria andMaxQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("max_qty not between", value1, value2, "maxQty");
            return (Criteria) this;
        }

        public Criteria andStatusIsNull() {
            addCriterion("status is null");
            return (Criteria) this;
        }

        public Criteria andStatusIsNotNull() {
            addCriterion("status is not null");
            return (Criteria) this;
        }

        public Criteria andStatusEqualTo(String value) {
            addCriterion("status =", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotEqualTo(String value) {
            addCriterion("status <>", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThan(String value) {
            addCriterion("status >", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusGreaterThanOrEqualTo(String value) {
            addCriterion("status >=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThan(String value) {
            addCriterion("status <", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLessThanOrEqualTo(String value) {
            addCriterion("status <=", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusLike(String value) {
            addCriterion("status like", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotLike(String value) {
            addCriterion("status not like", value, "status");
            return (Criteria) this;
        }

        public Criteria andStatusIn(List<String> values) {
            addCriterion("status in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotIn(List<String> values) {
            addCriterion("status not in", values, "status");
            return (Criteria) this;
        }

        public Criteria andStatusBetween(String value1, String value2) {
            addCriterion("status between", value1, value2, "status");
            return (Criteria) this;
        }

        public Criteria andStatusNotBetween(String value1, String value2) {
            addCriterion("status not between", value1, value2, "status");
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