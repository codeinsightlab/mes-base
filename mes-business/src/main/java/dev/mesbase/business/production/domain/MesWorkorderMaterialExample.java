// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesWorkorderMaterialExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesWorkorderMaterialExample() {
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

        public Criteria andTotalRequiredQtyIsNull() {
            addCriterion("total_required_qty is null");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyIsNotNull() {
            addCriterion("total_required_qty is not null");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyEqualTo(BigDecimal value) {
            addCriterion("total_required_qty =", value, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyNotEqualTo(BigDecimal value) {
            addCriterion("total_required_qty <>", value, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyGreaterThan(BigDecimal value) {
            addCriterion("total_required_qty >", value, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("total_required_qty >=", value, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyLessThan(BigDecimal value) {
            addCriterion("total_required_qty <", value, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("total_required_qty <=", value, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyIn(List<BigDecimal> values) {
            addCriterion("total_required_qty in", values, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyNotIn(List<BigDecimal> values) {
            addCriterion("total_required_qty not in", values, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("total_required_qty between", value1, value2, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andTotalRequiredQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("total_required_qty not between", value1, value2, "totalRequiredQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyIsNull() {
            addCriterion("planned_qty is null");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyIsNotNull() {
            addCriterion("planned_qty is not null");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyEqualTo(BigDecimal value) {
            addCriterion("planned_qty =", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyNotEqualTo(BigDecimal value) {
            addCriterion("planned_qty <>", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyGreaterThan(BigDecimal value) {
            addCriterion("planned_qty >", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("planned_qty >=", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyLessThan(BigDecimal value) {
            addCriterion("planned_qty <", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("planned_qty <=", value, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyIn(List<BigDecimal> values) {
            addCriterion("planned_qty in", values, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyNotIn(List<BigDecimal> values) {
            addCriterion("planned_qty not in", values, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("planned_qty between", value1, value2, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andPlannedQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("planned_qty not between", value1, value2, "plannedQty");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsIsNull() {
            addCriterion("warehouse_ids is null");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsIsNotNull() {
            addCriterion("warehouse_ids is not null");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsEqualTo(String value) {
            addCriterion("warehouse_ids =", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsNotEqualTo(String value) {
            addCriterion("warehouse_ids <>", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsGreaterThan(String value) {
            addCriterion("warehouse_ids >", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsGreaterThanOrEqualTo(String value) {
            addCriterion("warehouse_ids >=", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsLessThan(String value) {
            addCriterion("warehouse_ids <", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsLessThanOrEqualTo(String value) {
            addCriterion("warehouse_ids <=", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsLike(String value) {
            addCriterion("warehouse_ids like", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsNotLike(String value) {
            addCriterion("warehouse_ids not like", value, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsIn(List<String> values) {
            addCriterion("warehouse_ids in", values, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsNotIn(List<String> values) {
            addCriterion("warehouse_ids not in", values, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsBetween(String value1, String value2) {
            addCriterion("warehouse_ids between", value1, value2, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWarehouseIdsNotBetween(String value1, String value2) {
            addCriterion("warehouse_ids not between", value1, value2, "warehouseIds");
            return (Criteria) this;
        }

        public Criteria andWorkCenterIsNull() {
            addCriterion("work_center is null");
            return (Criteria) this;
        }

        public Criteria andWorkCenterIsNotNull() {
            addCriterion("work_center is not null");
            return (Criteria) this;
        }

        public Criteria andWorkCenterEqualTo(String value) {
            addCriterion("work_center =", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterNotEqualTo(String value) {
            addCriterion("work_center <>", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterGreaterThan(String value) {
            addCriterion("work_center >", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterGreaterThanOrEqualTo(String value) {
            addCriterion("work_center >=", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterLessThan(String value) {
            addCriterion("work_center <", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterLessThanOrEqualTo(String value) {
            addCriterion("work_center <=", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterLike(String value) {
            addCriterion("work_center like", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterNotLike(String value) {
            addCriterion("work_center not like", value, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterIn(List<String> values) {
            addCriterion("work_center in", values, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterNotIn(List<String> values) {
            addCriterion("work_center not in", values, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterBetween(String value1, String value2) {
            addCriterion("work_center between", value1, value2, "workCenter");
            return (Criteria) this;
        }

        public Criteria andWorkCenterNotBetween(String value1, String value2) {
            addCriterion("work_center not between", value1, value2, "workCenter");
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

        public Criteria andRevisionEqualTo(Long value) {
            addCriterion("revision =", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionNotEqualTo(Long value) {
            addCriterion("revision <>", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionGreaterThan(Long value) {
            addCriterion("revision >", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionGreaterThanOrEqualTo(Long value) {
            addCriterion("revision >=", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionLessThan(Long value) {
            addCriterion("revision <", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionLessThanOrEqualTo(Long value) {
            addCriterion("revision <=", value, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionIn(List<Long> values) {
            addCriterion("revision in", values, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionNotIn(List<Long> values) {
            addCriterion("revision not in", values, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionBetween(Long value1, Long value2) {
            addCriterion("revision between", value1, value2, "revision");
            return (Criteria) this;
        }

        public Criteria andRevisionNotBetween(Long value1, Long value2) {
            addCriterion("revision not between", value1, value2, "revision");
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

        public Criteria andUpdatedByIsNull() {
            addCriterion("updated_by is null");
            return (Criteria) this;
        }

        public Criteria andUpdatedByIsNotNull() {
            addCriterion("updated_by is not null");
            return (Criteria) this;
        }

        public Criteria andUpdatedByEqualTo(Long value) {
            addCriterion("updated_by =", value, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByNotEqualTo(Long value) {
            addCriterion("updated_by <>", value, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByGreaterThan(Long value) {
            addCriterion("updated_by >", value, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByGreaterThanOrEqualTo(Long value) {
            addCriterion("updated_by >=", value, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByLessThan(Long value) {
            addCriterion("updated_by <", value, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByLessThanOrEqualTo(Long value) {
            addCriterion("updated_by <=", value, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByIn(List<Long> values) {
            addCriterion("updated_by in", values, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByNotIn(List<Long> values) {
            addCriterion("updated_by not in", values, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByBetween(Long value1, Long value2) {
            addCriterion("updated_by between", value1, value2, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedByNotBetween(Long value1, Long value2) {
            addCriterion("updated_by not between", value1, value2, "updatedBy");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtIsNull() {
            addCriterion("updated_at is null");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtIsNotNull() {
            addCriterion("updated_at is not null");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtEqualTo(LocalDateTime value) {
            addCriterion("updated_at =", value, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtNotEqualTo(LocalDateTime value) {
            addCriterion("updated_at <>", value, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtGreaterThan(LocalDateTime value) {
            addCriterion("updated_at >", value, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("updated_at >=", value, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtLessThan(LocalDateTime value) {
            addCriterion("updated_at <", value, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("updated_at <=", value, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtIn(List<LocalDateTime> values) {
            addCriterion("updated_at in", values, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtNotIn(List<LocalDateTime> values) {
            addCriterion("updated_at not in", values, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("updated_at between", value1, value2, "updatedAt");
            return (Criteria) this;
        }

        public Criteria andUpdatedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("updated_at not between", value1, value2, "updatedAt");
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