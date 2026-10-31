// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MesProductionCombinationItemExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesProductionCombinationItemExample() {
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

        public Criteria andCombinationIdIsNull() {
            addCriterion("combination_id is null");
            return (Criteria) this;
        }

        public Criteria andCombinationIdIsNotNull() {
            addCriterion("combination_id is not null");
            return (Criteria) this;
        }

        public Criteria andCombinationIdEqualTo(Long value) {
            addCriterion("combination_id =", value, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdNotEqualTo(Long value) {
            addCriterion("combination_id <>", value, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdGreaterThan(Long value) {
            addCriterion("combination_id >", value, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdGreaterThanOrEqualTo(Long value) {
            addCriterion("combination_id >=", value, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdLessThan(Long value) {
            addCriterion("combination_id <", value, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdLessThanOrEqualTo(Long value) {
            addCriterion("combination_id <=", value, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdIn(List<Long> values) {
            addCriterion("combination_id in", values, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdNotIn(List<Long> values) {
            addCriterion("combination_id not in", values, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdBetween(Long value1, Long value2) {
            addCriterion("combination_id between", value1, value2, "combinationId");
            return (Criteria) this;
        }

        public Criteria andCombinationIdNotBetween(Long value1, Long value2) {
            addCriterion("combination_id not between", value1, value2, "combinationId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdIsNull() {
            addCriterion("demand_line_id is null");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdIsNotNull() {
            addCriterion("demand_line_id is not null");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdEqualTo(Long value) {
            addCriterion("demand_line_id =", value, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdNotEqualTo(Long value) {
            addCriterion("demand_line_id <>", value, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdGreaterThan(Long value) {
            addCriterion("demand_line_id >", value, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdGreaterThanOrEqualTo(Long value) {
            addCriterion("demand_line_id >=", value, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdLessThan(Long value) {
            addCriterion("demand_line_id <", value, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdLessThanOrEqualTo(Long value) {
            addCriterion("demand_line_id <=", value, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdIn(List<Long> values) {
            addCriterion("demand_line_id in", values, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdNotIn(List<Long> values) {
            addCriterion("demand_line_id not in", values, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdBetween(Long value1, Long value2) {
            addCriterion("demand_line_id between", value1, value2, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andDemandLineIdNotBetween(Long value1, Long value2) {
            addCriterion("demand_line_id not between", value1, value2, "demandLineId");
            return (Criteria) this;
        }

        public Criteria andLineNoIsNull() {
            addCriterion("line_no is null");
            return (Criteria) this;
        }

        public Criteria andLineNoIsNotNull() {
            addCriterion("line_no is not null");
            return (Criteria) this;
        }

        public Criteria andLineNoEqualTo(Integer value) {
            addCriterion("line_no =", value, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoNotEqualTo(Integer value) {
            addCriterion("line_no <>", value, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoGreaterThan(Integer value) {
            addCriterion("line_no >", value, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoGreaterThanOrEqualTo(Integer value) {
            addCriterion("line_no >=", value, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoLessThan(Integer value) {
            addCriterion("line_no <", value, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoLessThanOrEqualTo(Integer value) {
            addCriterion("line_no <=", value, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoIn(List<Integer> values) {
            addCriterion("line_no in", values, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoNotIn(List<Integer> values) {
            addCriterion("line_no not in", values, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoBetween(Integer value1, Integer value2) {
            addCriterion("line_no between", value1, value2, "lineNo");
            return (Criteria) this;
        }

        public Criteria andLineNoNotBetween(Integer value1, Integer value2) {
            addCriterion("line_no not between", value1, value2, "lineNo");
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

        public Criteria andMaterialCodeIsNull() {
            addCriterion("material_code is null");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeIsNotNull() {
            addCriterion("material_code is not null");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeEqualTo(String value) {
            addCriterion("material_code =", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeNotEqualTo(String value) {
            addCriterion("material_code <>", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeGreaterThan(String value) {
            addCriterion("material_code >", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeGreaterThanOrEqualTo(String value) {
            addCriterion("material_code >=", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeLessThan(String value) {
            addCriterion("material_code <", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeLessThanOrEqualTo(String value) {
            addCriterion("material_code <=", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeLike(String value) {
            addCriterion("material_code like", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeNotLike(String value) {
            addCriterion("material_code not like", value, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeIn(List<String> values) {
            addCriterion("material_code in", values, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeNotIn(List<String> values) {
            addCriterion("material_code not in", values, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeBetween(String value1, String value2) {
            addCriterion("material_code between", value1, value2, "materialCode");
            return (Criteria) this;
        }

        public Criteria andMaterialCodeNotBetween(String value1, String value2) {
            addCriterion("material_code not between", value1, value2, "materialCode");
            return (Criteria) this;
        }

        public Criteria andUnitIdIsNull() {
            addCriterion("unit_id is null");
            return (Criteria) this;
        }

        public Criteria andUnitIdIsNotNull() {
            addCriterion("unit_id is not null");
            return (Criteria) this;
        }

        public Criteria andUnitIdEqualTo(Long value) {
            addCriterion("unit_id =", value, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdNotEqualTo(Long value) {
            addCriterion("unit_id <>", value, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdGreaterThan(Long value) {
            addCriterion("unit_id >", value, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdGreaterThanOrEqualTo(Long value) {
            addCriterion("unit_id >=", value, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdLessThan(Long value) {
            addCriterion("unit_id <", value, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdLessThanOrEqualTo(Long value) {
            addCriterion("unit_id <=", value, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdIn(List<Long> values) {
            addCriterion("unit_id in", values, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdNotIn(List<Long> values) {
            addCriterion("unit_id not in", values, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdBetween(Long value1, Long value2) {
            addCriterion("unit_id between", value1, value2, "unitId");
            return (Criteria) this;
        }

        public Criteria andUnitIdNotBetween(Long value1, Long value2) {
            addCriterion("unit_id not between", value1, value2, "unitId");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyIsNull() {
            addCriterion("required_qty is null");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyIsNotNull() {
            addCriterion("required_qty is not null");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyEqualTo(BigDecimal value) {
            addCriterion("required_qty =", value, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyNotEqualTo(BigDecimal value) {
            addCriterion("required_qty <>", value, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyGreaterThan(BigDecimal value) {
            addCriterion("required_qty >", value, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("required_qty >=", value, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyLessThan(BigDecimal value) {
            addCriterion("required_qty <", value, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("required_qty <=", value, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyIn(List<BigDecimal> values) {
            addCriterion("required_qty in", values, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyNotIn(List<BigDecimal> values) {
            addCriterion("required_qty not in", values, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("required_qty between", value1, value2, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andRequiredQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("required_qty not between", value1, value2, "requiredQty");
            return (Criteria) this;
        }

        public Criteria andBatchNoIsNull() {
            addCriterion("batch_no is null");
            return (Criteria) this;
        }

        public Criteria andBatchNoIsNotNull() {
            addCriterion("batch_no is not null");
            return (Criteria) this;
        }

        public Criteria andBatchNoEqualTo(String value) {
            addCriterion("batch_no =", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotEqualTo(String value) {
            addCriterion("batch_no <>", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoGreaterThan(String value) {
            addCriterion("batch_no >", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoGreaterThanOrEqualTo(String value) {
            addCriterion("batch_no >=", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoLessThan(String value) {
            addCriterion("batch_no <", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoLessThanOrEqualTo(String value) {
            addCriterion("batch_no <=", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoLike(String value) {
            addCriterion("batch_no like", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotLike(String value) {
            addCriterion("batch_no not like", value, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoIn(List<String> values) {
            addCriterion("batch_no in", values, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotIn(List<String> values) {
            addCriterion("batch_no not in", values, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoBetween(String value1, String value2) {
            addCriterion("batch_no between", value1, value2, "batchNo");
            return (Criteria) this;
        }

        public Criteria andBatchNoNotBetween(String value1, String value2) {
            addCriterion("batch_no not between", value1, value2, "batchNo");
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