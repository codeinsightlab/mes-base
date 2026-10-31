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

        public Criteria andBomIdIsNull() {
            addCriterion("bom_id is null");
            return (Criteria) this;
        }

        public Criteria andBomIdIsNotNull() {
            addCriterion("bom_id is not null");
            return (Criteria) this;
        }

        public Criteria andBomIdEqualTo(Long value) {
            addCriterion("bom_id =", value, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdNotEqualTo(Long value) {
            addCriterion("bom_id <>", value, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdGreaterThan(Long value) {
            addCriterion("bom_id >", value, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdGreaterThanOrEqualTo(Long value) {
            addCriterion("bom_id >=", value, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdLessThan(Long value) {
            addCriterion("bom_id <", value, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdLessThanOrEqualTo(Long value) {
            addCriterion("bom_id <=", value, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdIn(List<Long> values) {
            addCriterion("bom_id in", values, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdNotIn(List<Long> values) {
            addCriterion("bom_id not in", values, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdBetween(Long value1, Long value2) {
            addCriterion("bom_id between", value1, value2, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomIdNotBetween(Long value1, Long value2) {
            addCriterion("bom_id not between", value1, value2, "bomId");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyIsNull() {
            addCriterion("bom_base_qty is null");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyIsNotNull() {
            addCriterion("bom_base_qty is not null");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyEqualTo(BigDecimal value) {
            addCriterion("bom_base_qty =", value, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyNotEqualTo(BigDecimal value) {
            addCriterion("bom_base_qty <>", value, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyGreaterThan(BigDecimal value) {
            addCriterion("bom_base_qty >", value, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("bom_base_qty >=", value, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyLessThan(BigDecimal value) {
            addCriterion("bom_base_qty <", value, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("bom_base_qty <=", value, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyIn(List<BigDecimal> values) {
            addCriterion("bom_base_qty in", values, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyNotIn(List<BigDecimal> values) {
            addCriterion("bom_base_qty not in", values, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("bom_base_qty between", value1, value2, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomBaseQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("bom_base_qty not between", value1, value2, "bomBaseQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyIsNull() {
            addCriterion("bom_component_qty is null");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyIsNotNull() {
            addCriterion("bom_component_qty is not null");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyEqualTo(BigDecimal value) {
            addCriterion("bom_component_qty =", value, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyNotEqualTo(BigDecimal value) {
            addCriterion("bom_component_qty <>", value, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyGreaterThan(BigDecimal value) {
            addCriterion("bom_component_qty >", value, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("bom_component_qty >=", value, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyLessThan(BigDecimal value) {
            addCriterion("bom_component_qty <", value, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("bom_component_qty <=", value, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyIn(List<BigDecimal> values) {
            addCriterion("bom_component_qty in", values, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyNotIn(List<BigDecimal> values) {
            addCriterion("bom_component_qty not in", values, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("bom_component_qty between", value1, value2, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andBomComponentQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("bom_component_qty not between", value1, value2, "bomComponentQty");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdIsNull() {
            addCriterion("output_unit_id is null");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdIsNotNull() {
            addCriterion("output_unit_id is not null");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdEqualTo(Long value) {
            addCriterion("output_unit_id =", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdNotEqualTo(Long value) {
            addCriterion("output_unit_id <>", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdGreaterThan(Long value) {
            addCriterion("output_unit_id >", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdGreaterThanOrEqualTo(Long value) {
            addCriterion("output_unit_id >=", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdLessThan(Long value) {
            addCriterion("output_unit_id <", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdLessThanOrEqualTo(Long value) {
            addCriterion("output_unit_id <=", value, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdIn(List<Long> values) {
            addCriterion("output_unit_id in", values, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdNotIn(List<Long> values) {
            addCriterion("output_unit_id not in", values, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdBetween(Long value1, Long value2) {
            addCriterion("output_unit_id between", value1, value2, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andOutputUnitIdNotBetween(Long value1, Long value2) {
            addCriterion("output_unit_id not between", value1, value2, "outputUnitId");
            return (Criteria) this;
        }

        public Criteria andFrozenAtIsNull() {
            addCriterion("frozen_at is null");
            return (Criteria) this;
        }

        public Criteria andFrozenAtIsNotNull() {
            addCriterion("frozen_at is not null");
            return (Criteria) this;
        }

        public Criteria andFrozenAtEqualTo(LocalDateTime value) {
            addCriterion("frozen_at =", value, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtNotEqualTo(LocalDateTime value) {
            addCriterion("frozen_at <>", value, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtGreaterThan(LocalDateTime value) {
            addCriterion("frozen_at >", value, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("frozen_at >=", value, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtLessThan(LocalDateTime value) {
            addCriterion("frozen_at <", value, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("frozen_at <=", value, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtIn(List<LocalDateTime> values) {
            addCriterion("frozen_at in", values, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtNotIn(List<LocalDateTime> values) {
            addCriterion("frozen_at not in", values, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("frozen_at between", value1, value2, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andFrozenAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("frozen_at not between", value1, value2, "frozenAt");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdIsNull() {
            addCriterion("workorder_id is null");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdIsNotNull() {
            addCriterion("workorder_id is not null");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdEqualTo(Long value) {
            addCriterion("workorder_id =", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdNotEqualTo(Long value) {
            addCriterion("workorder_id <>", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdGreaterThan(Long value) {
            addCriterion("workorder_id >", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdGreaterThanOrEqualTo(Long value) {
            addCriterion("workorder_id >=", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdLessThan(Long value) {
            addCriterion("workorder_id <", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdLessThanOrEqualTo(Long value) {
            addCriterion("workorder_id <=", value, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdIn(List<Long> values) {
            addCriterion("workorder_id in", values, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdNotIn(List<Long> values) {
            addCriterion("workorder_id not in", values, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdBetween(Long value1, Long value2) {
            addCriterion("workorder_id between", value1, value2, "workorderId");
            return (Criteria) this;
        }

        public Criteria andWorkorderIdNotBetween(Long value1, Long value2) {
            addCriterion("workorder_id not between", value1, value2, "workorderId");
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

        public Criteria andBomItemIdIsNull() {
            addCriterion("bom_item_id is null");
            return (Criteria) this;
        }

        public Criteria andBomItemIdIsNotNull() {
            addCriterion("bom_item_id is not null");
            return (Criteria) this;
        }

        public Criteria andBomItemIdEqualTo(Long value) {
            addCriterion("bom_item_id =", value, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdNotEqualTo(Long value) {
            addCriterion("bom_item_id <>", value, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdGreaterThan(Long value) {
            addCriterion("bom_item_id >", value, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdGreaterThanOrEqualTo(Long value) {
            addCriterion("bom_item_id >=", value, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdLessThan(Long value) {
            addCriterion("bom_item_id <", value, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdLessThanOrEqualTo(Long value) {
            addCriterion("bom_item_id <=", value, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdIn(List<Long> values) {
            addCriterion("bom_item_id in", values, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdNotIn(List<Long> values) {
            addCriterion("bom_item_id not in", values, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdBetween(Long value1, Long value2) {
            addCriterion("bom_item_id between", value1, value2, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andBomItemIdNotBetween(Long value1, Long value2) {
            addCriterion("bom_item_id not between", value1, value2, "bomItemId");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedIsNull() {
            addCriterion("manually_adjusted is null");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedIsNotNull() {
            addCriterion("manually_adjusted is not null");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedEqualTo(Boolean value) {
            addCriterion("manually_adjusted =", value, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedNotEqualTo(Boolean value) {
            addCriterion("manually_adjusted <>", value, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedGreaterThan(Boolean value) {
            addCriterion("manually_adjusted >", value, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedGreaterThanOrEqualTo(Boolean value) {
            addCriterion("manually_adjusted >=", value, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedLessThan(Boolean value) {
            addCriterion("manually_adjusted <", value, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedLessThanOrEqualTo(Boolean value) {
            addCriterion("manually_adjusted <=", value, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedIn(List<Boolean> values) {
            addCriterion("manually_adjusted in", values, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedNotIn(List<Boolean> values) {
            addCriterion("manually_adjusted not in", values, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedBetween(Boolean value1, Boolean value2) {
            addCriterion("manually_adjusted between", value1, value2, "manuallyAdjusted");
            return (Criteria) this;
        }

        public Criteria andManuallyAdjustedNotBetween(Boolean value1, Boolean value2) {
            addCriterion("manually_adjusted not between", value1, value2, "manuallyAdjusted");
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