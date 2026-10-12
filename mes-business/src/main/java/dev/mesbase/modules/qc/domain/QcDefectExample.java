// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.qc.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QcDefectExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public QcDefectExample() {
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

        public Criteria andDefectIdIsNull() {
            addCriterion("defect_id is null");
            return (Criteria) this;
        }

        public Criteria andDefectIdIsNotNull() {
            addCriterion("defect_id is not null");
            return (Criteria) this;
        }

        public Criteria andDefectIdEqualTo(Long value) {
            addCriterion("defect_id =", value, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdNotEqualTo(Long value) {
            addCriterion("defect_id <>", value, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdGreaterThan(Long value) {
            addCriterion("defect_id >", value, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdGreaterThanOrEqualTo(Long value) {
            addCriterion("defect_id >=", value, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdLessThan(Long value) {
            addCriterion("defect_id <", value, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdLessThanOrEqualTo(Long value) {
            addCriterion("defect_id <=", value, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdIn(List<Long> values) {
            addCriterion("defect_id in", values, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdNotIn(List<Long> values) {
            addCriterion("defect_id not in", values, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdBetween(Long value1, Long value2) {
            addCriterion("defect_id between", value1, value2, "defectId");
            return (Criteria) this;
        }

        public Criteria andDefectIdNotBetween(Long value1, Long value2) {
            addCriterion("defect_id not between", value1, value2, "defectId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdIsNull() {
            addCriterion("factory_id is null");
            return (Criteria) this;
        }

        public Criteria andFactoryIdIsNotNull() {
            addCriterion("factory_id is not null");
            return (Criteria) this;
        }

        public Criteria andFactoryIdEqualTo(String value) {
            addCriterion("factory_id =", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdNotEqualTo(String value) {
            addCriterion("factory_id <>", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdGreaterThan(String value) {
            addCriterion("factory_id >", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdGreaterThanOrEqualTo(String value) {
            addCriterion("factory_id >=", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdLessThan(String value) {
            addCriterion("factory_id <", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdLessThanOrEqualTo(String value) {
            addCriterion("factory_id <=", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdLike(String value) {
            addCriterion("factory_id like", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdNotLike(String value) {
            addCriterion("factory_id not like", value, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdIn(List<String> values) {
            addCriterion("factory_id in", values, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdNotIn(List<String> values) {
            addCriterion("factory_id not in", values, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdBetween(String value1, String value2) {
            addCriterion("factory_id between", value1, value2, "factoryId");
            return (Criteria) this;
        }

        public Criteria andFactoryIdNotBetween(String value1, String value2) {
            addCriterion("factory_id not between", value1, value2, "factoryId");
            return (Criteria) this;
        }

        public Criteria andDefectCodeIsNull() {
            addCriterion("defect_code is null");
            return (Criteria) this;
        }

        public Criteria andDefectCodeIsNotNull() {
            addCriterion("defect_code is not null");
            return (Criteria) this;
        }

        public Criteria andDefectCodeEqualTo(String value) {
            addCriterion("defect_code =", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeNotEqualTo(String value) {
            addCriterion("defect_code <>", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeGreaterThan(String value) {
            addCriterion("defect_code >", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeGreaterThanOrEqualTo(String value) {
            addCriterion("defect_code >=", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeLessThan(String value) {
            addCriterion("defect_code <", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeLessThanOrEqualTo(String value) {
            addCriterion("defect_code <=", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeLike(String value) {
            addCriterion("defect_code like", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeNotLike(String value) {
            addCriterion("defect_code not like", value, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeIn(List<String> values) {
            addCriterion("defect_code in", values, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeNotIn(List<String> values) {
            addCriterion("defect_code not in", values, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeBetween(String value1, String value2) {
            addCriterion("defect_code between", value1, value2, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectCodeNotBetween(String value1, String value2) {
            addCriterion("defect_code not between", value1, value2, "defectCode");
            return (Criteria) this;
        }

        public Criteria andDefectNameIsNull() {
            addCriterion("defect_name is null");
            return (Criteria) this;
        }

        public Criteria andDefectNameIsNotNull() {
            addCriterion("defect_name is not null");
            return (Criteria) this;
        }

        public Criteria andDefectNameEqualTo(String value) {
            addCriterion("defect_name =", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameNotEqualTo(String value) {
            addCriterion("defect_name <>", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameGreaterThan(String value) {
            addCriterion("defect_name >", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameGreaterThanOrEqualTo(String value) {
            addCriterion("defect_name >=", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameLessThan(String value) {
            addCriterion("defect_name <", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameLessThanOrEqualTo(String value) {
            addCriterion("defect_name <=", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameLike(String value) {
            addCriterion("defect_name like", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameNotLike(String value) {
            addCriterion("defect_name not like", value, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameIn(List<String> values) {
            addCriterion("defect_name in", values, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameNotIn(List<String> values) {
            addCriterion("defect_name not in", values, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameBetween(String value1, String value2) {
            addCriterion("defect_name between", value1, value2, "defectName");
            return (Criteria) this;
        }

        public Criteria andDefectNameNotBetween(String value1, String value2) {
            addCriterion("defect_name not between", value1, value2, "defectName");
            return (Criteria) this;
        }

        public Criteria andIndexTypeIsNull() {
            addCriterion("index_type is null");
            return (Criteria) this;
        }

        public Criteria andIndexTypeIsNotNull() {
            addCriterion("index_type is not null");
            return (Criteria) this;
        }

        public Criteria andIndexTypeEqualTo(String value) {
            addCriterion("index_type =", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeNotEqualTo(String value) {
            addCriterion("index_type <>", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeGreaterThan(String value) {
            addCriterion("index_type >", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeGreaterThanOrEqualTo(String value) {
            addCriterion("index_type >=", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeLessThan(String value) {
            addCriterion("index_type <", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeLessThanOrEqualTo(String value) {
            addCriterion("index_type <=", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeLike(String value) {
            addCriterion("index_type like", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeNotLike(String value) {
            addCriterion("index_type not like", value, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeIn(List<String> values) {
            addCriterion("index_type in", values, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeNotIn(List<String> values) {
            addCriterion("index_type not in", values, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeBetween(String value1, String value2) {
            addCriterion("index_type between", value1, value2, "indexType");
            return (Criteria) this;
        }

        public Criteria andIndexTypeNotBetween(String value1, String value2) {
            addCriterion("index_type not between", value1, value2, "indexType");
            return (Criteria) this;
        }

        public Criteria andDefectLevelIsNull() {
            addCriterion("defect_level is null");
            return (Criteria) this;
        }

        public Criteria andDefectLevelIsNotNull() {
            addCriterion("defect_level is not null");
            return (Criteria) this;
        }

        public Criteria andDefectLevelEqualTo(String value) {
            addCriterion("defect_level =", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelNotEqualTo(String value) {
            addCriterion("defect_level <>", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelGreaterThan(String value) {
            addCriterion("defect_level >", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelGreaterThanOrEqualTo(String value) {
            addCriterion("defect_level >=", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelLessThan(String value) {
            addCriterion("defect_level <", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelLessThanOrEqualTo(String value) {
            addCriterion("defect_level <=", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelLike(String value) {
            addCriterion("defect_level like", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelNotLike(String value) {
            addCriterion("defect_level not like", value, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelIn(List<String> values) {
            addCriterion("defect_level in", values, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelNotIn(List<String> values) {
            addCriterion("defect_level not in", values, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelBetween(String value1, String value2) {
            addCriterion("defect_level between", value1, value2, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andDefectLevelNotBetween(String value1, String value2) {
            addCriterion("defect_level not between", value1, value2, "defectLevel");
            return (Criteria) this;
        }

        public Criteria andRemarkIsNull() {
            addCriterion("remark is null");
            return (Criteria) this;
        }

        public Criteria andRemarkIsNotNull() {
            addCriterion("remark is not null");
            return (Criteria) this;
        }

        public Criteria andRemarkEqualTo(String value) {
            addCriterion("remark =", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotEqualTo(String value) {
            addCriterion("remark <>", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkGreaterThan(String value) {
            addCriterion("remark >", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkGreaterThanOrEqualTo(String value) {
            addCriterion("remark >=", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkLessThan(String value) {
            addCriterion("remark <", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkLessThanOrEqualTo(String value) {
            addCriterion("remark <=", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkLike(String value) {
            addCriterion("remark like", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotLike(String value) {
            addCriterion("remark not like", value, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkIn(List<String> values) {
            addCriterion("remark in", values, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotIn(List<String> values) {
            addCriterion("remark not in", values, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkBetween(String value1, String value2) {
            addCriterion("remark between", value1, value2, "remark");
            return (Criteria) this;
        }

        public Criteria andRemarkNotBetween(String value1, String value2) {
            addCriterion("remark not between", value1, value2, "remark");
            return (Criteria) this;
        }

        public Criteria andCreateByIsNull() {
            addCriterion("create_by is null");
            return (Criteria) this;
        }

        public Criteria andCreateByIsNotNull() {
            addCriterion("create_by is not null");
            return (Criteria) this;
        }

        public Criteria andCreateByEqualTo(String value) {
            addCriterion("create_by =", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByNotEqualTo(String value) {
            addCriterion("create_by <>", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByGreaterThan(String value) {
            addCriterion("create_by >", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByGreaterThanOrEqualTo(String value) {
            addCriterion("create_by >=", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByLessThan(String value) {
            addCriterion("create_by <", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByLessThanOrEqualTo(String value) {
            addCriterion("create_by <=", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByLike(String value) {
            addCriterion("create_by like", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByNotLike(String value) {
            addCriterion("create_by not like", value, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByIn(List<String> values) {
            addCriterion("create_by in", values, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByNotIn(List<String> values) {
            addCriterion("create_by not in", values, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByBetween(String value1, String value2) {
            addCriterion("create_by between", value1, value2, "createBy");
            return (Criteria) this;
        }

        public Criteria andCreateByNotBetween(String value1, String value2) {
            addCriterion("create_by not between", value1, value2, "createBy");
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

        public Criteria andUpdateByIsNull() {
            addCriterion("update_by is null");
            return (Criteria) this;
        }

        public Criteria andUpdateByIsNotNull() {
            addCriterion("update_by is not null");
            return (Criteria) this;
        }

        public Criteria andUpdateByEqualTo(String value) {
            addCriterion("update_by =", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByNotEqualTo(String value) {
            addCriterion("update_by <>", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByGreaterThan(String value) {
            addCriterion("update_by >", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByGreaterThanOrEqualTo(String value) {
            addCriterion("update_by >=", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByLessThan(String value) {
            addCriterion("update_by <", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByLessThanOrEqualTo(String value) {
            addCriterion("update_by <=", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByLike(String value) {
            addCriterion("update_by like", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByNotLike(String value) {
            addCriterion("update_by not like", value, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByIn(List<String> values) {
            addCriterion("update_by in", values, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByNotIn(List<String> values) {
            addCriterion("update_by not in", values, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByBetween(String value1, String value2) {
            addCriterion("update_by between", value1, value2, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateByNotBetween(String value1, String value2) {
            addCriterion("update_by not between", value1, value2, "updateBy");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeIsNull() {
            addCriterion("update_time is null");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeIsNotNull() {
            addCriterion("update_time is not null");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeEqualTo(LocalDateTime value) {
            addCriterion("update_time =", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeNotEqualTo(LocalDateTime value) {
            addCriterion("update_time <>", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeGreaterThan(LocalDateTime value) {
            addCriterion("update_time >", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("update_time >=", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeLessThan(LocalDateTime value) {
            addCriterion("update_time <", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("update_time <=", value, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeIn(List<LocalDateTime> values) {
            addCriterion("update_time in", values, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeNotIn(List<LocalDateTime> values) {
            addCriterion("update_time not in", values, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("update_time between", value1, value2, "updateTime");
            return (Criteria) this;
        }

        public Criteria andUpdateTimeNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("update_time not between", value1, value2, "updateTime");
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