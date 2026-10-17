// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesQualityTreatmentExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesQualityTreatmentExample() {
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

        public Criteria andDeclarationRevisionIsNull() {
            addCriterion("declaration_revision is null");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionIsNotNull() {
            addCriterion("declaration_revision is not null");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionEqualTo(Integer value) {
            addCriterion("declaration_revision =", value, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionNotEqualTo(Integer value) {
            addCriterion("declaration_revision <>", value, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionGreaterThan(Integer value) {
            addCriterion("declaration_revision >", value, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionGreaterThanOrEqualTo(Integer value) {
            addCriterion("declaration_revision >=", value, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionLessThan(Integer value) {
            addCriterion("declaration_revision <", value, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionLessThanOrEqualTo(Integer value) {
            addCriterion("declaration_revision <=", value, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionIn(List<Integer> values) {
            addCriterion("declaration_revision in", values, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionNotIn(List<Integer> values) {
            addCriterion("declaration_revision not in", values, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionBetween(Integer value1, Integer value2) {
            addCriterion("declaration_revision between", value1, value2, "declarationRevision");
            return (Criteria) this;
        }

        public Criteria andDeclarationRevisionNotBetween(Integer value1, Integer value2) {
            addCriterion("declaration_revision not between", value1, value2, "declarationRevision");
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

        public Criteria andParentIdIsNull() {
            addCriterion("parent_id is null");
            return (Criteria) this;
        }

        public Criteria andParentIdIsNotNull() {
            addCriterion("parent_id is not null");
            return (Criteria) this;
        }

        public Criteria andParentIdEqualTo(String value) {
            addCriterion("parent_id =", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdNotEqualTo(String value) {
            addCriterion("parent_id <>", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdGreaterThan(String value) {
            addCriterion("parent_id >", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdGreaterThanOrEqualTo(String value) {
            addCriterion("parent_id >=", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdLessThan(String value) {
            addCriterion("parent_id <", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdLessThanOrEqualTo(String value) {
            addCriterion("parent_id <=", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdLike(String value) {
            addCriterion("parent_id like", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdNotLike(String value) {
            addCriterion("parent_id not like", value, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdIn(List<String> values) {
            addCriterion("parent_id in", values, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdNotIn(List<String> values) {
            addCriterion("parent_id not in", values, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdBetween(String value1, String value2) {
            addCriterion("parent_id between", value1, value2, "parentId");
            return (Criteria) this;
        }

        public Criteria andParentIdNotBetween(String value1, String value2) {
            addCriterion("parent_id not between", value1, value2, "parentId");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeIsNull() {
            addCriterion("attribution_type is null");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeIsNotNull() {
            addCriterion("attribution_type is not null");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeEqualTo(String value) {
            addCriterion("attribution_type =", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeNotEqualTo(String value) {
            addCriterion("attribution_type <>", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeGreaterThan(String value) {
            addCriterion("attribution_type >", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeGreaterThanOrEqualTo(String value) {
            addCriterion("attribution_type >=", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeLessThan(String value) {
            addCriterion("attribution_type <", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeLessThanOrEqualTo(String value) {
            addCriterion("attribution_type <=", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeLike(String value) {
            addCriterion("attribution_type like", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeNotLike(String value) {
            addCriterion("attribution_type not like", value, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeIn(List<String> values) {
            addCriterion("attribution_type in", values, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeNotIn(List<String> values) {
            addCriterion("attribution_type not in", values, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeBetween(String value1, String value2) {
            addCriterion("attribution_type between", value1, value2, "attributionType");
            return (Criteria) this;
        }

        public Criteria andAttributionTypeNotBetween(String value1, String value2) {
            addCriterion("attribution_type not between", value1, value2, "attributionType");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdIsNull() {
            addCriterion("related_declaration_id is null");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdIsNotNull() {
            addCriterion("related_declaration_id is not null");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdEqualTo(String value) {
            addCriterion("related_declaration_id =", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdNotEqualTo(String value) {
            addCriterion("related_declaration_id <>", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdGreaterThan(String value) {
            addCriterion("related_declaration_id >", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdGreaterThanOrEqualTo(String value) {
            addCriterion("related_declaration_id >=", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdLessThan(String value) {
            addCriterion("related_declaration_id <", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdLessThanOrEqualTo(String value) {
            addCriterion("related_declaration_id <=", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdLike(String value) {
            addCriterion("related_declaration_id like", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdNotLike(String value) {
            addCriterion("related_declaration_id not like", value, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdIn(List<String> values) {
            addCriterion("related_declaration_id in", values, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdNotIn(List<String> values) {
            addCriterion("related_declaration_id not in", values, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdBetween(String value1, String value2) {
            addCriterion("related_declaration_id between", value1, value2, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andRelatedDeclarationIdNotBetween(String value1, String value2) {
            addCriterion("related_declaration_id not between", value1, value2, "relatedDeclarationId");
            return (Criteria) this;
        }

        public Criteria andBasisIsNull() {
            addCriterion("basis is null");
            return (Criteria) this;
        }

        public Criteria andBasisIsNotNull() {
            addCriterion("basis is not null");
            return (Criteria) this;
        }

        public Criteria andBasisEqualTo(String value) {
            addCriterion("basis =", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisNotEqualTo(String value) {
            addCriterion("basis <>", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisGreaterThan(String value) {
            addCriterion("basis >", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisGreaterThanOrEqualTo(String value) {
            addCriterion("basis >=", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisLessThan(String value) {
            addCriterion("basis <", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisLessThanOrEqualTo(String value) {
            addCriterion("basis <=", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisLike(String value) {
            addCriterion("basis like", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisNotLike(String value) {
            addCriterion("basis not like", value, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisIn(List<String> values) {
            addCriterion("basis in", values, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisNotIn(List<String> values) {
            addCriterion("basis not in", values, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisBetween(String value1, String value2) {
            addCriterion("basis between", value1, value2, "basis");
            return (Criteria) this;
        }

        public Criteria andBasisNotBetween(String value1, String value2) {
            addCriterion("basis not between", value1, value2, "basis");
            return (Criteria) this;
        }

        public Criteria andScrapQtyIsNull() {
            addCriterion("scrap_qty is null");
            return (Criteria) this;
        }

        public Criteria andScrapQtyIsNotNull() {
            addCriterion("scrap_qty is not null");
            return (Criteria) this;
        }

        public Criteria andScrapQtyEqualTo(BigDecimal value) {
            addCriterion("scrap_qty =", value, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyNotEqualTo(BigDecimal value) {
            addCriterion("scrap_qty <>", value, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyGreaterThan(BigDecimal value) {
            addCriterion("scrap_qty >", value, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("scrap_qty >=", value, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyLessThan(BigDecimal value) {
            addCriterion("scrap_qty <", value, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("scrap_qty <=", value, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyIn(List<BigDecimal> values) {
            addCriterion("scrap_qty in", values, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyNotIn(List<BigDecimal> values) {
            addCriterion("scrap_qty not in", values, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("scrap_qty between", value1, value2, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andScrapQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("scrap_qty not between", value1, value2, "scrapQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyIsNull() {
            addCriterion("rework_qty is null");
            return (Criteria) this;
        }

        public Criteria andReworkQtyIsNotNull() {
            addCriterion("rework_qty is not null");
            return (Criteria) this;
        }

        public Criteria andReworkQtyEqualTo(BigDecimal value) {
            addCriterion("rework_qty =", value, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyNotEqualTo(BigDecimal value) {
            addCriterion("rework_qty <>", value, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyGreaterThan(BigDecimal value) {
            addCriterion("rework_qty >", value, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("rework_qty >=", value, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyLessThan(BigDecimal value) {
            addCriterion("rework_qty <", value, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("rework_qty <=", value, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyIn(List<BigDecimal> values) {
            addCriterion("rework_qty in", values, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyNotIn(List<BigDecimal> values) {
            addCriterion("rework_qty not in", values, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("rework_qty between", value1, value2, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andReworkQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("rework_qty not between", value1, value2, "reworkQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyIsNull() {
            addCriterion("processed_qty is null");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyIsNotNull() {
            addCriterion("processed_qty is not null");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyEqualTo(BigDecimal value) {
            addCriterion("processed_qty =", value, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyNotEqualTo(BigDecimal value) {
            addCriterion("processed_qty <>", value, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyGreaterThan(BigDecimal value) {
            addCriterion("processed_qty >", value, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("processed_qty >=", value, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyLessThan(BigDecimal value) {
            addCriterion("processed_qty <", value, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("processed_qty <=", value, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyIn(List<BigDecimal> values) {
            addCriterion("processed_qty in", values, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyNotIn(List<BigDecimal> values) {
            addCriterion("processed_qty not in", values, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("processed_qty between", value1, value2, "processedQty");
            return (Criteria) this;
        }

        public Criteria andProcessedQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("processed_qty not between", value1, value2, "processedQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyIsNull() {
            addCriterion("recovered_qty is null");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyIsNotNull() {
            addCriterion("recovered_qty is not null");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyEqualTo(BigDecimal value) {
            addCriterion("recovered_qty =", value, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyNotEqualTo(BigDecimal value) {
            addCriterion("recovered_qty <>", value, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyGreaterThan(BigDecimal value) {
            addCriterion("recovered_qty >", value, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("recovered_qty >=", value, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyLessThan(BigDecimal value) {
            addCriterion("recovered_qty <", value, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("recovered_qty <=", value, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyIn(List<BigDecimal> values) {
            addCriterion("recovered_qty in", values, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyNotIn(List<BigDecimal> values) {
            addCriterion("recovered_qty not in", values, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("recovered_qty between", value1, value2, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andRecoveredQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("recovered_qty not between", value1, value2, "recoveredQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyIsNull() {
            addCriterion("failed_qty is null");
            return (Criteria) this;
        }

        public Criteria andFailedQtyIsNotNull() {
            addCriterion("failed_qty is not null");
            return (Criteria) this;
        }

        public Criteria andFailedQtyEqualTo(BigDecimal value) {
            addCriterion("failed_qty =", value, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyNotEqualTo(BigDecimal value) {
            addCriterion("failed_qty <>", value, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyGreaterThan(BigDecimal value) {
            addCriterion("failed_qty >", value, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyGreaterThanOrEqualTo(BigDecimal value) {
            addCriterion("failed_qty >=", value, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyLessThan(BigDecimal value) {
            addCriterion("failed_qty <", value, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyLessThanOrEqualTo(BigDecimal value) {
            addCriterion("failed_qty <=", value, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyIn(List<BigDecimal> values) {
            addCriterion("failed_qty in", values, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyNotIn(List<BigDecimal> values) {
            addCriterion("failed_qty not in", values, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("failed_qty between", value1, value2, "failedQty");
            return (Criteria) this;
        }

        public Criteria andFailedQtyNotBetween(BigDecimal value1, BigDecimal value2) {
            addCriterion("failed_qty not between", value1, value2, "failedQty");
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

        public Criteria andConfirmedByIsNull() {
            addCriterion("confirmed_by is null");
            return (Criteria) this;
        }

        public Criteria andConfirmedByIsNotNull() {
            addCriterion("confirmed_by is not null");
            return (Criteria) this;
        }

        public Criteria andConfirmedByEqualTo(Long value) {
            addCriterion("confirmed_by =", value, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByNotEqualTo(Long value) {
            addCriterion("confirmed_by <>", value, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByGreaterThan(Long value) {
            addCriterion("confirmed_by >", value, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByGreaterThanOrEqualTo(Long value) {
            addCriterion("confirmed_by >=", value, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByLessThan(Long value) {
            addCriterion("confirmed_by <", value, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByLessThanOrEqualTo(Long value) {
            addCriterion("confirmed_by <=", value, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByIn(List<Long> values) {
            addCriterion("confirmed_by in", values, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByNotIn(List<Long> values) {
            addCriterion("confirmed_by not in", values, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByBetween(Long value1, Long value2) {
            addCriterion("confirmed_by between", value1, value2, "confirmedBy");
            return (Criteria) this;
        }

        public Criteria andConfirmedByNotBetween(Long value1, Long value2) {
            addCriterion("confirmed_by not between", value1, value2, "confirmedBy");
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