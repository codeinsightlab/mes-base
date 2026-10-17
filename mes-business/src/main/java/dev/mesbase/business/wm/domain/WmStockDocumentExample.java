// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WmStockDocumentExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public WmStockDocumentExample() {
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

        public Criteria andDocumentNoIsNull() {
            addCriterion("document_no is null");
            return (Criteria) this;
        }

        public Criteria andDocumentNoIsNotNull() {
            addCriterion("document_no is not null");
            return (Criteria) this;
        }

        public Criteria andDocumentNoEqualTo(String value) {
            addCriterion("document_no =", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoNotEqualTo(String value) {
            addCriterion("document_no <>", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoGreaterThan(String value) {
            addCriterion("document_no >", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoGreaterThanOrEqualTo(String value) {
            addCriterion("document_no >=", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoLessThan(String value) {
            addCriterion("document_no <", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoLessThanOrEqualTo(String value) {
            addCriterion("document_no <=", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoLike(String value) {
            addCriterion("document_no like", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoNotLike(String value) {
            addCriterion("document_no not like", value, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoIn(List<String> values) {
            addCriterion("document_no in", values, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoNotIn(List<String> values) {
            addCriterion("document_no not in", values, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoBetween(String value1, String value2) {
            addCriterion("document_no between", value1, value2, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentNoNotBetween(String value1, String value2) {
            addCriterion("document_no not between", value1, value2, "documentNo");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeIsNull() {
            addCriterion("document_type is null");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeIsNotNull() {
            addCriterion("document_type is not null");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeEqualTo(String value) {
            addCriterion("document_type =", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeNotEqualTo(String value) {
            addCriterion("document_type <>", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeGreaterThan(String value) {
            addCriterion("document_type >", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeGreaterThanOrEqualTo(String value) {
            addCriterion("document_type >=", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeLessThan(String value) {
            addCriterion("document_type <", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeLessThanOrEqualTo(String value) {
            addCriterion("document_type <=", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeLike(String value) {
            addCriterion("document_type like", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeNotLike(String value) {
            addCriterion("document_type not like", value, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeIn(List<String> values) {
            addCriterion("document_type in", values, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeNotIn(List<String> values) {
            addCriterion("document_type not in", values, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeBetween(String value1, String value2) {
            addCriterion("document_type between", value1, value2, "documentType");
            return (Criteria) this;
        }

        public Criteria andDocumentTypeNotBetween(String value1, String value2) {
            addCriterion("document_type not between", value1, value2, "documentType");
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

        public Criteria andSourceWorkorderIdIsNull() {
            addCriterion("source_workorder_id is null");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdIsNotNull() {
            addCriterion("source_workorder_id is not null");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdEqualTo(Long value) {
            addCriterion("source_workorder_id =", value, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdNotEqualTo(Long value) {
            addCriterion("source_workorder_id <>", value, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdGreaterThan(Long value) {
            addCriterion("source_workorder_id >", value, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdGreaterThanOrEqualTo(Long value) {
            addCriterion("source_workorder_id >=", value, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdLessThan(Long value) {
            addCriterion("source_workorder_id <", value, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdLessThanOrEqualTo(Long value) {
            addCriterion("source_workorder_id <=", value, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdIn(List<Long> values) {
            addCriterion("source_workorder_id in", values, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdNotIn(List<Long> values) {
            addCriterion("source_workorder_id not in", values, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdBetween(Long value1, Long value2) {
            addCriterion("source_workorder_id between", value1, value2, "sourceWorkorderId");
            return (Criteria) this;
        }

        public Criteria andSourceWorkorderIdNotBetween(Long value1, Long value2) {
            addCriterion("source_workorder_id not between", value1, value2, "sourceWorkorderId");
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

        public Criteria andPostedByIsNull() {
            addCriterion("posted_by is null");
            return (Criteria) this;
        }

        public Criteria andPostedByIsNotNull() {
            addCriterion("posted_by is not null");
            return (Criteria) this;
        }

        public Criteria andPostedByEqualTo(Long value) {
            addCriterion("posted_by =", value, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByNotEqualTo(Long value) {
            addCriterion("posted_by <>", value, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByGreaterThan(Long value) {
            addCriterion("posted_by >", value, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByGreaterThanOrEqualTo(Long value) {
            addCriterion("posted_by >=", value, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByLessThan(Long value) {
            addCriterion("posted_by <", value, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByLessThanOrEqualTo(Long value) {
            addCriterion("posted_by <=", value, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByIn(List<Long> values) {
            addCriterion("posted_by in", values, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByNotIn(List<Long> values) {
            addCriterion("posted_by not in", values, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByBetween(Long value1, Long value2) {
            addCriterion("posted_by between", value1, value2, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedByNotBetween(Long value1, Long value2) {
            addCriterion("posted_by not between", value1, value2, "postedBy");
            return (Criteria) this;
        }

        public Criteria andPostedAtIsNull() {
            addCriterion("posted_at is null");
            return (Criteria) this;
        }

        public Criteria andPostedAtIsNotNull() {
            addCriterion("posted_at is not null");
            return (Criteria) this;
        }

        public Criteria andPostedAtEqualTo(LocalDateTime value) {
            addCriterion("posted_at =", value, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtNotEqualTo(LocalDateTime value) {
            addCriterion("posted_at <>", value, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtGreaterThan(LocalDateTime value) {
            addCriterion("posted_at >", value, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("posted_at >=", value, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtLessThan(LocalDateTime value) {
            addCriterion("posted_at <", value, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("posted_at <=", value, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtIn(List<LocalDateTime> values) {
            addCriterion("posted_at in", values, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtNotIn(List<LocalDateTime> values) {
            addCriterion("posted_at not in", values, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("posted_at between", value1, value2, "postedAt");
            return (Criteria) this;
        }

        public Criteria andPostedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("posted_at not between", value1, value2, "postedAt");
            return (Criteria) this;
        }

        public Criteria andCancelledByIsNull() {
            addCriterion("cancelled_by is null");
            return (Criteria) this;
        }

        public Criteria andCancelledByIsNotNull() {
            addCriterion("cancelled_by is not null");
            return (Criteria) this;
        }

        public Criteria andCancelledByEqualTo(Long value) {
            addCriterion("cancelled_by =", value, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByNotEqualTo(Long value) {
            addCriterion("cancelled_by <>", value, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByGreaterThan(Long value) {
            addCriterion("cancelled_by >", value, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByGreaterThanOrEqualTo(Long value) {
            addCriterion("cancelled_by >=", value, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByLessThan(Long value) {
            addCriterion("cancelled_by <", value, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByLessThanOrEqualTo(Long value) {
            addCriterion("cancelled_by <=", value, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByIn(List<Long> values) {
            addCriterion("cancelled_by in", values, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByNotIn(List<Long> values) {
            addCriterion("cancelled_by not in", values, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByBetween(Long value1, Long value2) {
            addCriterion("cancelled_by between", value1, value2, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledByNotBetween(Long value1, Long value2) {
            addCriterion("cancelled_by not between", value1, value2, "cancelledBy");
            return (Criteria) this;
        }

        public Criteria andCancelledAtIsNull() {
            addCriterion("cancelled_at is null");
            return (Criteria) this;
        }

        public Criteria andCancelledAtIsNotNull() {
            addCriterion("cancelled_at is not null");
            return (Criteria) this;
        }

        public Criteria andCancelledAtEqualTo(LocalDateTime value) {
            addCriterion("cancelled_at =", value, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtNotEqualTo(LocalDateTime value) {
            addCriterion("cancelled_at <>", value, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtGreaterThan(LocalDateTime value) {
            addCriterion("cancelled_at >", value, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("cancelled_at >=", value, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtLessThan(LocalDateTime value) {
            addCriterion("cancelled_at <", value, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("cancelled_at <=", value, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtIn(List<LocalDateTime> values) {
            addCriterion("cancelled_at in", values, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtNotIn(List<LocalDateTime> values) {
            addCriterion("cancelled_at not in", values, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("cancelled_at between", value1, value2, "cancelledAt");
            return (Criteria) this;
        }

        public Criteria andCancelledAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("cancelled_at not between", value1, value2, "cancelledAt");
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

        public Criteria andPostRequestIdIsNull() {
            addCriterion("post_request_id is null");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdIsNotNull() {
            addCriterion("post_request_id is not null");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdEqualTo(String value) {
            addCriterion("post_request_id =", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdNotEqualTo(String value) {
            addCriterion("post_request_id <>", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdGreaterThan(String value) {
            addCriterion("post_request_id >", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdGreaterThanOrEqualTo(String value) {
            addCriterion("post_request_id >=", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdLessThan(String value) {
            addCriterion("post_request_id <", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdLessThanOrEqualTo(String value) {
            addCriterion("post_request_id <=", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdLike(String value) {
            addCriterion("post_request_id like", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdNotLike(String value) {
            addCriterion("post_request_id not like", value, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdIn(List<String> values) {
            addCriterion("post_request_id in", values, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdNotIn(List<String> values) {
            addCriterion("post_request_id not in", values, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdBetween(String value1, String value2) {
            addCriterion("post_request_id between", value1, value2, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestIdNotBetween(String value1, String value2) {
            addCriterion("post_request_id not between", value1, value2, "postRequestId");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashIsNull() {
            addCriterion("post_request_hash is null");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashIsNotNull() {
            addCriterion("post_request_hash is not null");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashEqualTo(String value) {
            addCriterion("post_request_hash =", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashNotEqualTo(String value) {
            addCriterion("post_request_hash <>", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashGreaterThan(String value) {
            addCriterion("post_request_hash >", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashGreaterThanOrEqualTo(String value) {
            addCriterion("post_request_hash >=", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashLessThan(String value) {
            addCriterion("post_request_hash <", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashLessThanOrEqualTo(String value) {
            addCriterion("post_request_hash <=", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashLike(String value) {
            addCriterion("post_request_hash like", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashNotLike(String value) {
            addCriterion("post_request_hash not like", value, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashIn(List<String> values) {
            addCriterion("post_request_hash in", values, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashNotIn(List<String> values) {
            addCriterion("post_request_hash not in", values, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashBetween(String value1, String value2) {
            addCriterion("post_request_hash between", value1, value2, "postRequestHash");
            return (Criteria) this;
        }

        public Criteria andPostRequestHashNotBetween(String value1, String value2) {
            addCriterion("post_request_hash not between", value1, value2, "postRequestHash");
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