// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WmStockActionExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public WmStockActionExample() {
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

        public Criteria andBinCodeIsNull() {
            addCriterion("bin_code is null");
            return (Criteria) this;
        }

        public Criteria andBinCodeIsNotNull() {
            addCriterion("bin_code is not null");
            return (Criteria) this;
        }

        public Criteria andBinCodeEqualTo(String value) {
            addCriterion("bin_code =", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeNotEqualTo(String value) {
            addCriterion("bin_code <>", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeGreaterThan(String value) {
            addCriterion("bin_code >", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeGreaterThanOrEqualTo(String value) {
            addCriterion("bin_code >=", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeLessThan(String value) {
            addCriterion("bin_code <", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeLessThanOrEqualTo(String value) {
            addCriterion("bin_code <=", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeLike(String value) {
            addCriterion("bin_code like", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeNotLike(String value) {
            addCriterion("bin_code not like", value, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeIn(List<String> values) {
            addCriterion("bin_code in", values, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeNotIn(List<String> values) {
            addCriterion("bin_code not in", values, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeBetween(String value1, String value2) {
            addCriterion("bin_code between", value1, value2, "binCode");
            return (Criteria) this;
        }

        public Criteria andBinCodeNotBetween(String value1, String value2) {
            addCriterion("bin_code not between", value1, value2, "binCode");
            return (Criteria) this;
        }

        public Criteria andActionTypeIsNull() {
            addCriterion("action_type is null");
            return (Criteria) this;
        }

        public Criteria andActionTypeIsNotNull() {
            addCriterion("action_type is not null");
            return (Criteria) this;
        }

        public Criteria andActionTypeEqualTo(String value) {
            addCriterion("action_type =", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeNotEqualTo(String value) {
            addCriterion("action_type <>", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeGreaterThan(String value) {
            addCriterion("action_type >", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeGreaterThanOrEqualTo(String value) {
            addCriterion("action_type >=", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeLessThan(String value) {
            addCriterion("action_type <", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeLessThanOrEqualTo(String value) {
            addCriterion("action_type <=", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeLike(String value) {
            addCriterion("action_type like", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeNotLike(String value) {
            addCriterion("action_type not like", value, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeIn(List<String> values) {
            addCriterion("action_type in", values, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeNotIn(List<String> values) {
            addCriterion("action_type not in", values, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeBetween(String value1, String value2) {
            addCriterion("action_type between", value1, value2, "actionType");
            return (Criteria) this;
        }

        public Criteria andActionTypeNotBetween(String value1, String value2) {
            addCriterion("action_type not between", value1, value2, "actionType");
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

        public Criteria andAvailableDeltaIsNull() {
            addCriterion("available_delta is null");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaIsNotNull() {
            addCriterion("available_delta is not null");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaEqualTo(BigDecimal value) {
            addCriterion("available_delta =", value, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaNotEqualTo(BigDecimal value) {
            addCriterion("available_delta <>", value, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaGreaterThan(BigDecimal value) {
            addCriterion("available_delta >", value, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("available_delta >=", value, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaLessThan(BigDecimal value) {
            addCriterion("available_delta <", value, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaLessThanOrEqualTo(BigDecimal value) {
            addCriterion("available_delta <=", value, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaIn(List<BigDecimal> values) {
            addCriterion("available_delta in", values, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaNotIn(List<BigDecimal> values) {
            addCriterion("available_delta not in", values, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("available_delta between", value1, value2, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andAvailableDeltaNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("available_delta not between", value1, value2, "availableDelta");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeIsNull() {
            addCriterion("quantity_before is null");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeIsNotNull() {
            addCriterion("quantity_before is not null");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeEqualTo(BigDecimal value) {
            addCriterion("quantity_before =", value, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeNotEqualTo(BigDecimal value) {
            addCriterion("quantity_before <>", value, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeGreaterThan(BigDecimal value) {
            addCriterion("quantity_before >", value, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("quantity_before >=", value, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeLessThan(BigDecimal value) {
            addCriterion("quantity_before <", value, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeLessThanOrEqualTo(BigDecimal value) {
            addCriterion("quantity_before <=", value, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeIn(List<BigDecimal> values) {
            addCriterion("quantity_before in", values, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeNotIn(List<BigDecimal> values) {
            addCriterion("quantity_before not in", values, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("quantity_before between", value1, value2, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityBeforeNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("quantity_before not between", value1, value2, "quantityBefore");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterIsNull() {
            addCriterion("quantity_after is null");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterIsNotNull() {
            addCriterion("quantity_after is not null");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterEqualTo(BigDecimal value) {
            addCriterion("quantity_after =", value, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterNotEqualTo(BigDecimal value) {
            addCriterion("quantity_after <>", value, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterGreaterThan(BigDecimal value) {
            addCriterion("quantity_after >", value, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("quantity_after >=", value, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterLessThan(BigDecimal value) {
            addCriterion("quantity_after <", value, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterLessThanOrEqualTo(BigDecimal value) {
            addCriterion("quantity_after <=", value, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterIn(List<BigDecimal> values) {
            addCriterion("quantity_after in", values, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterNotIn(List<BigDecimal> values) {
            addCriterion("quantity_after not in", values, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("quantity_after between", value1, value2, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andQuantityAfterNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("quantity_after not between", value1, value2, "quantityAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeIsNull() {
            addCriterion("available_before is null");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeIsNotNull() {
            addCriterion("available_before is not null");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeEqualTo(BigDecimal value) {
            addCriterion("available_before =", value, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeNotEqualTo(BigDecimal value) {
            addCriterion("available_before <>", value, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeGreaterThan(BigDecimal value) {
            addCriterion("available_before >", value, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("available_before >=", value, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeLessThan(BigDecimal value) {
            addCriterion("available_before <", value, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeLessThanOrEqualTo(BigDecimal value) {
            addCriterion("available_before <=", value, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeIn(List<BigDecimal> values) {
            addCriterion("available_before in", values, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeNotIn(List<BigDecimal> values) {
            addCriterion("available_before not in", values, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("available_before between", value1, value2, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableBeforeNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("available_before not between", value1, value2, "availableBefore");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterIsNull() {
            addCriterion("available_after is null");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterIsNotNull() {
            addCriterion("available_after is not null");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterEqualTo(BigDecimal value) {
            addCriterion("available_after =", value, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterNotEqualTo(BigDecimal value) {
            addCriterion("available_after <>", value, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterGreaterThan(BigDecimal value) {
            addCriterion("available_after >", value, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("available_after >=", value, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterLessThan(BigDecimal value) {
            addCriterion("available_after <", value, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterLessThanOrEqualTo(BigDecimal value) {
            addCriterion("available_after <=", value, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterIn(List<BigDecimal> values) {
            addCriterion("available_after in", values, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterNotIn(List<BigDecimal> values) {
            addCriterion("available_after not in", values, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("available_after between", value1, value2, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andAvailableAfterNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("available_after not between", value1, value2, "availableAfter");
            return (Criteria) this;
        }

        public Criteria andSourceTypeIsNull() {
            addCriterion("source_type is null");
            return (Criteria) this;
        }

        public Criteria andSourceTypeIsNotNull() {
            addCriterion("source_type is not null");
            return (Criteria) this;
        }

        public Criteria andSourceTypeEqualTo(String value) {
            addCriterion("source_type =", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeNotEqualTo(String value) {
            addCriterion("source_type <>", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeGreaterThan(String value) {
            addCriterion("source_type >", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeGreaterThanOrEqualTo(String value) {
            addCriterion("source_type >=", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeLessThan(String value) {
            addCriterion("source_type <", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeLessThanOrEqualTo(String value) {
            addCriterion("source_type <=", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeLike(String value) {
            addCriterion("source_type like", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeNotLike(String value) {
            addCriterion("source_type not like", value, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeIn(List<String> values) {
            addCriterion("source_type in", values, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeNotIn(List<String> values) {
            addCriterion("source_type not in", values, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeBetween(String value1, String value2) {
            addCriterion("source_type between", value1, value2, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceTypeNotBetween(String value1, String value2) {
            addCriterion("source_type not between", value1, value2, "sourceType");
            return (Criteria) this;
        }

        public Criteria andSourceIdIsNull() {
            addCriterion("source_id is null");
            return (Criteria) this;
        }

        public Criteria andSourceIdIsNotNull() {
            addCriterion("source_id is not null");
            return (Criteria) this;
        }

        public Criteria andSourceIdEqualTo(String value) {
            addCriterion("source_id =", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdNotEqualTo(String value) {
            addCriterion("source_id <>", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdGreaterThan(String value) {
            addCriterion("source_id >", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdGreaterThanOrEqualTo(String value) {
            addCriterion("source_id >=", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdLessThan(String value) {
            addCriterion("source_id <", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdLessThanOrEqualTo(String value) {
            addCriterion("source_id <=", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdLike(String value) {
            addCriterion("source_id like", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdNotLike(String value) {
            addCriterion("source_id not like", value, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdIn(List<String> values) {
            addCriterion("source_id in", values, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdNotIn(List<String> values) {
            addCriterion("source_id not in", values, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdBetween(String value1, String value2) {
            addCriterion("source_id between", value1, value2, "sourceId");
            return (Criteria) this;
        }

        public Criteria andSourceIdNotBetween(String value1, String value2) {
            addCriterion("source_id not between", value1, value2, "sourceId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdIsNull() {
            addCriterion("reference_action_id is null");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdIsNotNull() {
            addCriterion("reference_action_id is not null");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdEqualTo(String value) {
            addCriterion("reference_action_id =", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdNotEqualTo(String value) {
            addCriterion("reference_action_id <>", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdGreaterThan(String value) {
            addCriterion("reference_action_id >", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdGreaterThanOrEqualTo(String value) {
            addCriterion("reference_action_id >=", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdLessThan(String value) {
            addCriterion("reference_action_id <", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdLessThanOrEqualTo(String value) {
            addCriterion("reference_action_id <=", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdLike(String value) {
            addCriterion("reference_action_id like", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdNotLike(String value) {
            addCriterion("reference_action_id not like", value, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdIn(List<String> values) {
            addCriterion("reference_action_id in", values, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdNotIn(List<String> values) {
            addCriterion("reference_action_id not in", values, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdBetween(String value1, String value2) {
            addCriterion("reference_action_id between", value1, value2, "referenceActionId");
            return (Criteria) this;
        }

        public Criteria andReferenceActionIdNotBetween(String value1, String value2) {
            addCriterion("reference_action_id not between", value1, value2, "referenceActionId");
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

        public Criteria andDocumentLineIdIsNull() {
            addCriterion("document_line_id is null");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdIsNotNull() {
            addCriterion("document_line_id is not null");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdEqualTo(Long value) {
            addCriterion("document_line_id =", value, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdNotEqualTo(Long value) {
            addCriterion("document_line_id <>", value, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdGreaterThan(Long value) {
            addCriterion("document_line_id >", value, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdGreaterThanOrEqualTo(Long value) {
            addCriterion("document_line_id >=", value, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdLessThan(Long value) {
            addCriterion("document_line_id <", value, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdLessThanOrEqualTo(Long value) {
            addCriterion("document_line_id <=", value, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdIn(List<Long> values) {
            addCriterion("document_line_id in", values, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdNotIn(List<Long> values) {
            addCriterion("document_line_id not in", values, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdBetween(Long value1, Long value2) {
            addCriterion("document_line_id between", value1, value2, "documentLineId");
            return (Criteria) this;
        }

        public Criteria andDocumentLineIdNotBetween(Long value1, Long value2) {
            addCriterion("document_line_id not between", value1, value2, "documentLineId");
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

        public Criteria andOperationIdEqualTo(String value) {
            addCriterion("operation_id =", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotEqualTo(String value) {
            addCriterion("operation_id <>", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdGreaterThan(String value) {
            addCriterion("operation_id >", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdGreaterThanOrEqualTo(String value) {
            addCriterion("operation_id >=", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdLessThan(String value) {
            addCriterion("operation_id <", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdLessThanOrEqualTo(String value) {
            addCriterion("operation_id <=", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdLike(String value) {
            addCriterion("operation_id like", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotLike(String value) {
            addCriterion("operation_id not like", value, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdIn(List<String> values) {
            addCriterion("operation_id in", values, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotIn(List<String> values) {
            addCriterion("operation_id not in", values, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdBetween(String value1, String value2) {
            addCriterion("operation_id between", value1, value2, "operationId");
            return (Criteria) this;
        }

        public Criteria andOperationIdNotBetween(String value1, String value2) {
            addCriterion("operation_id not between", value1, value2, "operationId");
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