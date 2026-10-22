package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesProductionDefectExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesProductionDefectExample() {
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

        public Criteria andDeclarationIdIsNull() {
            addCriterion("declaration_id is null");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdIsNotNull() {
            addCriterion("declaration_id is not null");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdEqualTo(String value) {
            addCriterion("declaration_id =", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdNotEqualTo(String value) {
            addCriterion("declaration_id <>", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdGreaterThan(String value) {
            addCriterion("declaration_id >", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdGreaterThanOrEqualTo(String value) {
            addCriterion("declaration_id >=", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdLessThan(String value) {
            addCriterion("declaration_id <", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdLessThanOrEqualTo(String value) {
            addCriterion("declaration_id <=", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdLike(String value) {
            addCriterion("declaration_id like", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdNotLike(String value) {
            addCriterion("declaration_id not like", value, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdIn(List<String> values) {
            addCriterion("declaration_id in", values, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdNotIn(List<String> values) {
            addCriterion("declaration_id not in", values, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdBetween(String value1, String value2) {
            addCriterion("declaration_id between", value1, value2, "declarationId");
            return (Criteria) this;
        }

        public Criteria andDeclarationIdNotBetween(String value1, String value2) {
            addCriterion("declaration_id not between", value1, value2, "declarationId");
            return (Criteria) this;
        }

        public Criteria andRevisionNoIsNull() {
            addCriterion("revision_no is null");
            return (Criteria) this;
        }

        public Criteria andRevisionNoIsNotNull() {
            addCriterion("revision_no is not null");
            return (Criteria) this;
        }

        public Criteria andRevisionNoEqualTo(Integer value) {
            addCriterion("revision_no =", value, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoNotEqualTo(Integer value) {
            addCriterion("revision_no <>", value, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoGreaterThan(Integer value) {
            addCriterion("revision_no >", value, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoGreaterThanOrEqualTo(Integer value) {
            addCriterion("revision_no >=", value, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoLessThan(Integer value) {
            addCriterion("revision_no <", value, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoLessThanOrEqualTo(Integer value) {
            addCriterion("revision_no <=", value, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoIn(List<Integer> values) {
            addCriterion("revision_no in", values, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoNotIn(List<Integer> values) {
            addCriterion("revision_no not in", values, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoBetween(Integer value1, Integer value2) {
            addCriterion("revision_no between", value1, value2, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRevisionNoNotBetween(Integer value1, Integer value2) {
            addCriterion("revision_no not between", value1, value2, "revisionNo");
            return (Criteria) this;
        }

        public Criteria andRecordActionIsNull() {
            addCriterion("record_action is null");
            return (Criteria) this;
        }

        public Criteria andRecordActionIsNotNull() {
            addCriterion("record_action is not null");
            return (Criteria) this;
        }

        public Criteria andRecordActionEqualTo(String value) {
            addCriterion("record_action =", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionNotEqualTo(String value) {
            addCriterion("record_action <>", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionGreaterThan(String value) {
            addCriterion("record_action >", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionGreaterThanOrEqualTo(String value) {
            addCriterion("record_action >=", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionLessThan(String value) {
            addCriterion("record_action <", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionLessThanOrEqualTo(String value) {
            addCriterion("record_action <=", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionLike(String value) {
            addCriterion("record_action like", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionNotLike(String value) {
            addCriterion("record_action not like", value, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionIn(List<String> values) {
            addCriterion("record_action in", values, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionNotIn(List<String> values) {
            addCriterion("record_action not in", values, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionBetween(String value1, String value2) {
            addCriterion("record_action between", value1, value2, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRecordActionNotBetween(String value1, String value2) {
            addCriterion("record_action not between", value1, value2, "recordAction");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonIsNull() {
            addCriterion("revision_reason is null");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonIsNotNull() {
            addCriterion("revision_reason is not null");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonEqualTo(String value) {
            addCriterion("revision_reason =", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonNotEqualTo(String value) {
            addCriterion("revision_reason <>", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonGreaterThan(String value) {
            addCriterion("revision_reason >", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonGreaterThanOrEqualTo(String value) {
            addCriterion("revision_reason >=", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonLessThan(String value) {
            addCriterion("revision_reason <", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonLessThanOrEqualTo(String value) {
            addCriterion("revision_reason <=", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonLike(String value) {
            addCriterion("revision_reason like", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonNotLike(String value) {
            addCriterion("revision_reason not like", value, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonIn(List<String> values) {
            addCriterion("revision_reason in", values, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonNotIn(List<String> values) {
            addCriterion("revision_reason not in", values, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonBetween(String value1, String value2) {
            addCriterion("revision_reason between", value1, value2, "revisionReason");
            return (Criteria) this;
        }

        public Criteria andRevisionReasonNotBetween(String value1, String value2) {
            addCriterion("revision_reason not between", value1, value2, "revisionReason");
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

        public Criteria andAffectedNodeIdIsNull() {
            addCriterion("affected_node_id is null");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdIsNotNull() {
            addCriterion("affected_node_id is not null");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdEqualTo(Long value) {
            addCriterion("affected_node_id =", value, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdNotEqualTo(Long value) {
            addCriterion("affected_node_id <>", value, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdGreaterThan(Long value) {
            addCriterion("affected_node_id >", value, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdGreaterThanOrEqualTo(Long value) {
            addCriterion("affected_node_id >=", value, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdLessThan(Long value) {
            addCriterion("affected_node_id <", value, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdLessThanOrEqualTo(Long value) {
            addCriterion("affected_node_id <=", value, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdIn(List<Long> values) {
            addCriterion("affected_node_id in", values, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdNotIn(List<Long> values) {
            addCriterion("affected_node_id not in", values, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdBetween(Long value1, Long value2) {
            addCriterion("affected_node_id between", value1, value2, "affectedNodeId");
            return (Criteria) this;
        }

        public Criteria andAffectedNodeIdNotBetween(Long value1, Long value2) {
            addCriterion("affected_node_id not between", value1, value2, "affectedNodeId");
            return (Criteria) this;
        }


        public Criteria andDefectQtyIsNull() {
            addCriterion("defect_qty is null");
            return (Criteria) this;
        }

        public Criteria andDefectQtyIsNotNull() {
            addCriterion("defect_qty is not null");
            return (Criteria) this;
        }

        public Criteria andDefectQtyEqualTo(BigDecimal value) {
            addCriterion("defect_qty =", value, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyNotEqualTo(BigDecimal value) {
            addCriterion("defect_qty <>", value, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyGreaterThan(BigDecimal value) {
            addCriterion("defect_qty >", value, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("defect_qty >=", value, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyLessThan(BigDecimal value) {
            addCriterion("defect_qty <", value, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("defect_qty <=", value, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyIn(List<BigDecimal> values) {
            addCriterion("defect_qty in", values, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyNotIn(List<BigDecimal> values) {
            addCriterion("defect_qty not in", values, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("defect_qty between", value1, value2, "defectQty");
            return (Criteria) this;
        }

        public Criteria andDefectQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("defect_qty not between", value1, value2, "defectQty");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryIsNull() {
            addCriterion("waste_category is null");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryIsNotNull() {
            addCriterion("waste_category is not null");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryEqualTo(String value) {
            addCriterion("waste_category =", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryNotEqualTo(String value) {
            addCriterion("waste_category <>", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryGreaterThan(String value) {
            addCriterion("waste_category >", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryGreaterThanOrEqualTo(String value) {
            addCriterion("waste_category >=", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryLessThan(String value) {
            addCriterion("waste_category <", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryLessThanOrEqualTo(String value) {
            addCriterion("waste_category <=", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryLike(String value) {
            addCriterion("waste_category like", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryNotLike(String value) {
            addCriterion("waste_category not like", value, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryIn(List<String> values) {
            addCriterion("waste_category in", values, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryNotIn(List<String> values) {
            addCriterion("waste_category not in", values, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryBetween(String value1, String value2) {
            addCriterion("waste_category between", value1, value2, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andWasteCategoryNotBetween(String value1, String value2) {
            addCriterion("waste_category not between", value1, value2, "wasteCategory");
            return (Criteria) this;
        }

        public Criteria andDefectReasonIsNull() {
            addCriterion("defect_reason is null");
            return (Criteria) this;
        }

        public Criteria andDefectReasonIsNotNull() {
            addCriterion("defect_reason is not null");
            return (Criteria) this;
        }

        public Criteria andDefectReasonEqualTo(String value) {
            addCriterion("defect_reason =", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonNotEqualTo(String value) {
            addCriterion("defect_reason <>", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonGreaterThan(String value) {
            addCriterion("defect_reason >", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonGreaterThanOrEqualTo(String value) {
            addCriterion("defect_reason >=", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonLessThan(String value) {
            addCriterion("defect_reason <", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonLessThanOrEqualTo(String value) {
            addCriterion("defect_reason <=", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonLike(String value) {
            addCriterion("defect_reason like", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonNotLike(String value) {
            addCriterion("defect_reason not like", value, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonIn(List<String> values) {
            addCriterion("defect_reason in", values, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonNotIn(List<String> values) {
            addCriterion("defect_reason not in", values, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonBetween(String value1, String value2) {
            addCriterion("defect_reason between", value1, value2, "defectReason");
            return (Criteria) this;
        }

        public Criteria andDefectReasonNotBetween(String value1, String value2) {
            addCriterion("defect_reason not between", value1, value2, "defectReason");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdIsNull() {
            addCriterion("responsible_user_id is null");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdIsNotNull() {
            addCriterion("responsible_user_id is not null");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdEqualTo(Long value) {
            addCriterion("responsible_user_id =", value, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdNotEqualTo(Long value) {
            addCriterion("responsible_user_id <>", value, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdGreaterThan(Long value) {
            addCriterion("responsible_user_id >", value, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdGreaterThanOrEqualTo(Long value) {
            addCriterion("responsible_user_id >=", value, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdLessThan(Long value) {
            addCriterion("responsible_user_id <", value, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdLessThanOrEqualTo(Long value) {
            addCriterion("responsible_user_id <=", value, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdIn(List<Long> values) {
            addCriterion("responsible_user_id in", values, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdNotIn(List<Long> values) {
            addCriterion("responsible_user_id not in", values, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdBetween(Long value1, Long value2) {
            addCriterion("responsible_user_id between", value1, value2, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibleUserIdNotBetween(Long value1, Long value2) {
            addCriterion("responsible_user_id not between", value1, value2, "responsibleUserId");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkIsNull() {
            addCriterion("responsibility_remark is null");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkIsNotNull() {
            addCriterion("responsibility_remark is not null");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkEqualTo(String value) {
            addCriterion("responsibility_remark =", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkNotEqualTo(String value) {
            addCriterion("responsibility_remark <>", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkGreaterThan(String value) {
            addCriterion("responsibility_remark >", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkGreaterThanOrEqualTo(String value) {
            addCriterion("responsibility_remark >=", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkLessThan(String value) {
            addCriterion("responsibility_remark <", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkLessThanOrEqualTo(String value) {
            addCriterion("responsibility_remark <=", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkLike(String value) {
            addCriterion("responsibility_remark like", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkNotLike(String value) {
            addCriterion("responsibility_remark not like", value, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkIn(List<String> values) {
            addCriterion("responsibility_remark in", values, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkNotIn(List<String> values) {
            addCriterion("responsibility_remark not in", values, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkBetween(String value1, String value2) {
            addCriterion("responsibility_remark between", value1, value2, "responsibilityRemark");
            return (Criteria) this;
        }

        public Criteria andResponsibilityRemarkNotBetween(String value1, String value2) {
            addCriterion("responsibility_remark not between", value1, value2, "responsibilityRemark");
            return (Criteria) this;
        }


        public Criteria andRequestKeyIsNull() {
            addCriterion("request_key is null");
            return (Criteria) this;
        }

        public Criteria andRequestKeyIsNotNull() {
            addCriterion("request_key is not null");
            return (Criteria) this;
        }

        public Criteria andRequestKeyEqualTo(String value) {
            addCriterion("request_key =", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyNotEqualTo(String value) {
            addCriterion("request_key <>", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyGreaterThan(String value) {
            addCriterion("request_key >", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyGreaterThanOrEqualTo(String value) {
            addCriterion("request_key >=", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyLessThan(String value) {
            addCriterion("request_key <", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyLessThanOrEqualTo(String value) {
            addCriterion("request_key <=", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyLike(String value) {
            addCriterion("request_key like", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyNotLike(String value) {
            addCriterion("request_key not like", value, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyIn(List<String> values) {
            addCriterion("request_key in", values, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyNotIn(List<String> values) {
            addCriterion("request_key not in", values, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyBetween(String value1, String value2) {
            addCriterion("request_key between", value1, value2, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestKeyNotBetween(String value1, String value2) {
            addCriterion("request_key not between", value1, value2, "requestKey");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintIsNull() {
            addCriterion("request_fingerprint is null");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintIsNotNull() {
            addCriterion("request_fingerprint is not null");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintEqualTo(String value) {
            addCriterion("request_fingerprint =", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintNotEqualTo(String value) {
            addCriterion("request_fingerprint <>", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintGreaterThan(String value) {
            addCriterion("request_fingerprint >", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintGreaterThanOrEqualTo(String value) {
            addCriterion("request_fingerprint >=", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintLessThan(String value) {
            addCriterion("request_fingerprint <", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintLessThanOrEqualTo(String value) {
            addCriterion("request_fingerprint <=", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintLike(String value) {
            addCriterion("request_fingerprint like", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintNotLike(String value) {
            addCriterion("request_fingerprint not like", value, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintIn(List<String> values) {
            addCriterion("request_fingerprint in", values, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintNotIn(List<String> values) {
            addCriterion("request_fingerprint not in", values, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintBetween(String value1, String value2) {
            addCriterion("request_fingerprint between", value1, value2, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andRequestFingerprintNotBetween(String value1, String value2) {
            addCriterion("request_fingerprint not between", value1, value2, "requestFingerprint");
            return (Criteria) this;
        }

        public Criteria andReportedByIsNull() {
            addCriterion("reported_by is null");
            return (Criteria) this;
        }

        public Criteria andReportedByIsNotNull() {
            addCriterion("reported_by is not null");
            return (Criteria) this;
        }

        public Criteria andReportedByEqualTo(Long value) {
            addCriterion("reported_by =", value, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByNotEqualTo(Long value) {
            addCriterion("reported_by <>", value, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByGreaterThan(Long value) {
            addCriterion("reported_by >", value, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByGreaterThanOrEqualTo(Long value) {
            addCriterion("reported_by >=", value, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByLessThan(Long value) {
            addCriterion("reported_by <", value, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByLessThanOrEqualTo(Long value) {
            addCriterion("reported_by <=", value, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByIn(List<Long> values) {
            addCriterion("reported_by in", values, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByNotIn(List<Long> values) {
            addCriterion("reported_by not in", values, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByBetween(Long value1, Long value2) {
            addCriterion("reported_by between", value1, value2, "reportedBy");
            return (Criteria) this;
        }

        public Criteria andReportedByNotBetween(Long value1, Long value2) {
            addCriterion("reported_by not between", value1, value2, "reportedBy");
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

        public Criteria andRecordedByIsNull() {
            addCriterion("recorded_by is null");
            return (Criteria) this;
        }

        public Criteria andRecordedByIsNotNull() {
            addCriterion("recorded_by is not null");
            return (Criteria) this;
        }

        public Criteria andRecordedByEqualTo(Long value) {
            addCriterion("recorded_by =", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByNotEqualTo(Long value) {
            addCriterion("recorded_by <>", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByGreaterThan(Long value) {
            addCriterion("recorded_by >", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByGreaterThanOrEqualTo(Long value) {
            addCriterion("recorded_by >=", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByLessThan(Long value) {
            addCriterion("recorded_by <", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByLessThanOrEqualTo(Long value) {
            addCriterion("recorded_by <=", value, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByIn(List<Long> values) {
            addCriterion("recorded_by in", values, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByNotIn(List<Long> values) {
            addCriterion("recorded_by not in", values, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByBetween(Long value1, Long value2) {
            addCriterion("recorded_by between", value1, value2, "recordedBy");
            return (Criteria) this;
        }

        public Criteria andRecordedByNotBetween(Long value1, Long value2) {
            addCriterion("recorded_by not between", value1, value2, "recordedBy");
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

        public Criteria andDefectTypeIdIsNull() {
            addCriterion("defect_type_id is null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdIsNotNull() {
            addCriterion("defect_type_id is not null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdEqualTo(Long value) {
            addCriterion("defect_type_id =", value, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdNotEqualTo(Long value) {
            addCriterion("defect_type_id <>", value, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdGreaterThan(Long value) {
            addCriterion("defect_type_id >", value, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdGreaterThanOrEqualTo(Long value) {
            addCriterion("defect_type_id >=", value, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdLessThan(Long value) {
            addCriterion("defect_type_id <", value, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdLessThanOrEqualTo(Long value) {
            addCriterion("defect_type_id <=", value, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdIn(List<Long> values) {
            addCriterion("defect_type_id in", values, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdNotIn(List<Long> values) {
            addCriterion("defect_type_id not in", values, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdBetween(Long value1, Long value2) {
            addCriterion("defect_type_id between", value1, value2, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeIdNotBetween(Long value1, Long value2) {
            addCriterion("defect_type_id not between", value1, value2, "defectTypeId");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotIsNull() {
            addCriterion("defect_type_code_snapshot is null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotIsNotNull() {
            addCriterion("defect_type_code_snapshot is not null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotEqualTo(String value) {
            addCriterion("defect_type_code_snapshot =", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotNotEqualTo(String value) {
            addCriterion("defect_type_code_snapshot <>", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotGreaterThan(String value) {
            addCriterion("defect_type_code_snapshot >", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotGreaterThanOrEqualTo(String value) {
            addCriterion("defect_type_code_snapshot >=", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotLessThan(String value) {
            addCriterion("defect_type_code_snapshot <", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotLessThanOrEqualTo(String value) {
            addCriterion("defect_type_code_snapshot <=", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotLike(String value) {
            addCriterion("defect_type_code_snapshot like", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotNotLike(String value) {
            addCriterion("defect_type_code_snapshot not like", value, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotIn(List<String> values) {
            addCriterion("defect_type_code_snapshot in", values, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotNotIn(List<String> values) {
            addCriterion("defect_type_code_snapshot not in", values, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotBetween(String value1, String value2) {
            addCriterion("defect_type_code_snapshot between", value1, value2, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeCodeSnapshotNotBetween(String value1, String value2) {
            addCriterion("defect_type_code_snapshot not between", value1, value2, "defectTypeCodeSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotIsNull() {
            addCriterion("defect_type_name_snapshot is null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotIsNotNull() {
            addCriterion("defect_type_name_snapshot is not null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotEqualTo(String value) {
            addCriterion("defect_type_name_snapshot =", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotNotEqualTo(String value) {
            addCriterion("defect_type_name_snapshot <>", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotGreaterThan(String value) {
            addCriterion("defect_type_name_snapshot >", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotGreaterThanOrEqualTo(String value) {
            addCriterion("defect_type_name_snapshot >=", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotLessThan(String value) {
            addCriterion("defect_type_name_snapshot <", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotLessThanOrEqualTo(String value) {
            addCriterion("defect_type_name_snapshot <=", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotLike(String value) {
            addCriterion("defect_type_name_snapshot like", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotNotLike(String value) {
            addCriterion("defect_type_name_snapshot not like", value, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotIn(List<String> values) {
            addCriterion("defect_type_name_snapshot in", values, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotNotIn(List<String> values) {
            addCriterion("defect_type_name_snapshot not in", values, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotBetween(String value1, String value2) {
            addCriterion("defect_type_name_snapshot between", value1, value2, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeNameSnapshotNotBetween(String value1, String value2) {
            addCriterion("defect_type_name_snapshot not between", value1, value2, "defectTypeNameSnapshot");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionIsNull() {
            addCriterion("defect_type_revision is null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionIsNotNull() {
            addCriterion("defect_type_revision is not null");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionEqualTo(Integer value) {
            addCriterion("defect_type_revision =", value, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionNotEqualTo(Integer value) {
            addCriterion("defect_type_revision <>", value, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionGreaterThan(Integer value) {
            addCriterion("defect_type_revision >", value, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionGreaterThanOrEqualTo(Integer value) {
            addCriterion("defect_type_revision >=", value, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionLessThan(Integer value) {
            addCriterion("defect_type_revision <", value, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionLessThanOrEqualTo(Integer value) {
            addCriterion("defect_type_revision <=", value, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionIn(List<Integer> values) {
            addCriterion("defect_type_revision in", values, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionNotIn(List<Integer> values) {
            addCriterion("defect_type_revision not in", values, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionBetween(Integer value1, Integer value2) {
            addCriterion("defect_type_revision between", value1, value2, "defectTypeRevision");
            return (Criteria) this;
        }

        public Criteria andDefectTypeRevisionNotBetween(Integer value1, Integer value2) {
            addCriterion("defect_type_revision not between", value1, value2, "defectTypeRevision");
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