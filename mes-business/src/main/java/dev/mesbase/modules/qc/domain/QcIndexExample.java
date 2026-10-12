// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.qc.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QcIndexExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public QcIndexExample() {
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

        public Criteria andIndexIdIsNull() {
            addCriterion("index_id is null");
            return (Criteria) this;
        }

        public Criteria andIndexIdIsNotNull() {
            addCriterion("index_id is not null");
            return (Criteria) this;
        }

        public Criteria andIndexIdEqualTo(Long value) {
            addCriterion("index_id =", value, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdNotEqualTo(Long value) {
            addCriterion("index_id <>", value, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdGreaterThan(Long value) {
            addCriterion("index_id >", value, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdGreaterThanOrEqualTo(Long value) {
            addCriterion("index_id >=", value, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdLessThan(Long value) {
            addCriterion("index_id <", value, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdLessThanOrEqualTo(Long value) {
            addCriterion("index_id <=", value, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdIn(List<Long> values) {
            addCriterion("index_id in", values, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdNotIn(List<Long> values) {
            addCriterion("index_id not in", values, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdBetween(Long value1, Long value2) {
            addCriterion("index_id between", value1, value2, "indexId");
            return (Criteria) this;
        }

        public Criteria andIndexIdNotBetween(Long value1, Long value2) {
            addCriterion("index_id not between", value1, value2, "indexId");
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

        public Criteria andIndexCodeIsNull() {
            addCriterion("index_code is null");
            return (Criteria) this;
        }

        public Criteria andIndexCodeIsNotNull() {
            addCriterion("index_code is not null");
            return (Criteria) this;
        }

        public Criteria andIndexCodeEqualTo(String value) {
            addCriterion("index_code =", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeNotEqualTo(String value) {
            addCriterion("index_code <>", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeGreaterThan(String value) {
            addCriterion("index_code >", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeGreaterThanOrEqualTo(String value) {
            addCriterion("index_code >=", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeLessThan(String value) {
            addCriterion("index_code <", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeLessThanOrEqualTo(String value) {
            addCriterion("index_code <=", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeLike(String value) {
            addCriterion("index_code like", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeNotLike(String value) {
            addCriterion("index_code not like", value, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeIn(List<String> values) {
            addCriterion("index_code in", values, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeNotIn(List<String> values) {
            addCriterion("index_code not in", values, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeBetween(String value1, String value2) {
            addCriterion("index_code between", value1, value2, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexCodeNotBetween(String value1, String value2) {
            addCriterion("index_code not between", value1, value2, "indexCode");
            return (Criteria) this;
        }

        public Criteria andIndexNameIsNull() {
            addCriterion("index_name is null");
            return (Criteria) this;
        }

        public Criteria andIndexNameIsNotNull() {
            addCriterion("index_name is not null");
            return (Criteria) this;
        }

        public Criteria andIndexNameEqualTo(String value) {
            addCriterion("index_name =", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameNotEqualTo(String value) {
            addCriterion("index_name <>", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameGreaterThan(String value) {
            addCriterion("index_name >", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameGreaterThanOrEqualTo(String value) {
            addCriterion("index_name >=", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameLessThan(String value) {
            addCriterion("index_name <", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameLessThanOrEqualTo(String value) {
            addCriterion("index_name <=", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameLike(String value) {
            addCriterion("index_name like", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameNotLike(String value) {
            addCriterion("index_name not like", value, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameIn(List<String> values) {
            addCriterion("index_name in", values, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameNotIn(List<String> values) {
            addCriterion("index_name not in", values, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameBetween(String value1, String value2) {
            addCriterion("index_name between", value1, value2, "indexName");
            return (Criteria) this;
        }

        public Criteria andIndexNameNotBetween(String value1, String value2) {
            addCriterion("index_name not between", value1, value2, "indexName");
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

        public Criteria andQcToolIsNull() {
            addCriterion("qc_tool is null");
            return (Criteria) this;
        }

        public Criteria andQcToolIsNotNull() {
            addCriterion("qc_tool is not null");
            return (Criteria) this;
        }

        public Criteria andQcToolEqualTo(String value) {
            addCriterion("qc_tool =", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolNotEqualTo(String value) {
            addCriterion("qc_tool <>", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolGreaterThan(String value) {
            addCriterion("qc_tool >", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolGreaterThanOrEqualTo(String value) {
            addCriterion("qc_tool >=", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolLessThan(String value) {
            addCriterion("qc_tool <", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolLessThanOrEqualTo(String value) {
            addCriterion("qc_tool <=", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolLike(String value) {
            addCriterion("qc_tool like", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolNotLike(String value) {
            addCriterion("qc_tool not like", value, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolIn(List<String> values) {
            addCriterion("qc_tool in", values, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolNotIn(List<String> values) {
            addCriterion("qc_tool not in", values, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolBetween(String value1, String value2) {
            addCriterion("qc_tool between", value1, value2, "qcTool");
            return (Criteria) this;
        }

        public Criteria andQcToolNotBetween(String value1, String value2) {
            addCriterion("qc_tool not between", value1, value2, "qcTool");
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