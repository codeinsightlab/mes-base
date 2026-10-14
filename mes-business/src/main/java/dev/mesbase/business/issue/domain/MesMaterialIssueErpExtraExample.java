// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MesMaterialIssueErpExtraExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public MesMaterialIssueErpExtraExample() {
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

        public Criteria andSourceExecutionModeIsNull() {
            addCriterion("source_execution_mode is null");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeIsNotNull() {
            addCriterion("source_execution_mode is not null");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeEqualTo(String value) {
            addCriterion("source_execution_mode =", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeNotEqualTo(String value) {
            addCriterion("source_execution_mode <>", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeGreaterThan(String value) {
            addCriterion("source_execution_mode >", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeGreaterThanOrEqualTo(String value) {
            addCriterion("source_execution_mode >=", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeLessThan(String value) {
            addCriterion("source_execution_mode <", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeLessThanOrEqualTo(String value) {
            addCriterion("source_execution_mode <=", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeLike(String value) {
            addCriterion("source_execution_mode like", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeNotLike(String value) {
            addCriterion("source_execution_mode not like", value, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeIn(List<String> values) {
            addCriterion("source_execution_mode in", values, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeNotIn(List<String> values) {
            addCriterion("source_execution_mode not in", values, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeBetween(String value1, String value2) {
            addCriterion("source_execution_mode between", value1, value2, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andSourceExecutionModeNotBetween(String value1, String value2) {
            addCriterion("source_execution_mode not between", value1, value2, "sourceExecutionMode");
            return (Criteria) this;
        }

        public Criteria andExternalEffectIsNull() {
            addCriterion("external_effect is null");
            return (Criteria) this;
        }

        public Criteria andExternalEffectIsNotNull() {
            addCriterion("external_effect is not null");
            return (Criteria) this;
        }

        public Criteria andExternalEffectEqualTo(String value) {
            addCriterion("external_effect =", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectNotEqualTo(String value) {
            addCriterion("external_effect <>", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectGreaterThan(String value) {
            addCriterion("external_effect >", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectGreaterThanOrEqualTo(String value) {
            addCriterion("external_effect >=", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectLessThan(String value) {
            addCriterion("external_effect <", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectLessThanOrEqualTo(String value) {
            addCriterion("external_effect <=", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectLike(String value) {
            addCriterion("external_effect like", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectNotLike(String value) {
            addCriterion("external_effect not like", value, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectIn(List<String> values) {
            addCriterion("external_effect in", values, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectNotIn(List<String> values) {
            addCriterion("external_effect not in", values, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectBetween(String value1, String value2) {
            addCriterion("external_effect between", value1, value2, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalEffectNotBetween(String value1, String value2) {
            addCriterion("external_effect not between", value1, value2, "externalEffect");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyIsNull() {
            addCriterion("external_binding_key is null");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyIsNotNull() {
            addCriterion("external_binding_key is not null");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyEqualTo(String value) {
            addCriterion("external_binding_key =", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyNotEqualTo(String value) {
            addCriterion("external_binding_key <>", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyGreaterThan(String value) {
            addCriterion("external_binding_key >", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyGreaterThanOrEqualTo(String value) {
            addCriterion("external_binding_key >=", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyLessThan(String value) {
            addCriterion("external_binding_key <", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyLessThanOrEqualTo(String value) {
            addCriterion("external_binding_key <=", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyLike(String value) {
            addCriterion("external_binding_key like", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyNotLike(String value) {
            addCriterion("external_binding_key not like", value, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyIn(List<String> values) {
            addCriterion("external_binding_key in", values, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyNotIn(List<String> values) {
            addCriterion("external_binding_key not in", values, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyBetween(String value1, String value2) {
            addCriterion("external_binding_key between", value1, value2, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalBindingKeyNotBetween(String value1, String value2) {
            addCriterion("external_binding_key not between", value1, value2, "externalBindingKey");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceIsNull() {
            addCriterion("external_reference is null");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceIsNotNull() {
            addCriterion("external_reference is not null");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceEqualTo(String value) {
            addCriterion("external_reference =", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceNotEqualTo(String value) {
            addCriterion("external_reference <>", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceGreaterThan(String value) {
            addCriterion("external_reference >", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceGreaterThanOrEqualTo(String value) {
            addCriterion("external_reference >=", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceLessThan(String value) {
            addCriterion("external_reference <", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceLessThanOrEqualTo(String value) {
            addCriterion("external_reference <=", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceLike(String value) {
            addCriterion("external_reference like", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceNotLike(String value) {
            addCriterion("external_reference not like", value, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceIn(List<String> values) {
            addCriterion("external_reference in", values, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceNotIn(List<String> values) {
            addCriterion("external_reference not in", values, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceBetween(String value1, String value2) {
            addCriterion("external_reference between", value1, value2, "externalReference");
            return (Criteria) this;
        }

        public Criteria andExternalReferenceNotBetween(String value1, String value2) {
            addCriterion("external_reference not between", value1, value2, "externalReference");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusIsNull() {
            addCriterion("void_result_status is null");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusIsNotNull() {
            addCriterion("void_result_status is not null");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusEqualTo(String value) {
            addCriterion("void_result_status =", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusNotEqualTo(String value) {
            addCriterion("void_result_status <>", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusGreaterThan(String value) {
            addCriterion("void_result_status >", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusGreaterThanOrEqualTo(String value) {
            addCriterion("void_result_status >=", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusLessThan(String value) {
            addCriterion("void_result_status <", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusLessThanOrEqualTo(String value) {
            addCriterion("void_result_status <=", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusLike(String value) {
            addCriterion("void_result_status like", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusNotLike(String value) {
            addCriterion("void_result_status not like", value, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusIn(List<String> values) {
            addCriterion("void_result_status in", values, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusNotIn(List<String> values) {
            addCriterion("void_result_status not in", values, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusBetween(String value1, String value2) {
            addCriterion("void_result_status between", value1, value2, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidResultStatusNotBetween(String value1, String value2) {
            addCriterion("void_result_status not between", value1, value2, "voidResultStatus");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeIsNull() {
            addCriterion("void_execution_mode is null");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeIsNotNull() {
            addCriterion("void_execution_mode is not null");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeEqualTo(String value) {
            addCriterion("void_execution_mode =", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeNotEqualTo(String value) {
            addCriterion("void_execution_mode <>", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeGreaterThan(String value) {
            addCriterion("void_execution_mode >", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeGreaterThanOrEqualTo(String value) {
            addCriterion("void_execution_mode >=", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeLessThan(String value) {
            addCriterion("void_execution_mode <", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeLessThanOrEqualTo(String value) {
            addCriterion("void_execution_mode <=", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeLike(String value) {
            addCriterion("void_execution_mode like", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeNotLike(String value) {
            addCriterion("void_execution_mode not like", value, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeIn(List<String> values) {
            addCriterion("void_execution_mode in", values, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeNotIn(List<String> values) {
            addCriterion("void_execution_mode not in", values, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeBetween(String value1, String value2) {
            addCriterion("void_execution_mode between", value1, value2, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidExecutionModeNotBetween(String value1, String value2) {
            addCriterion("void_execution_mode not between", value1, value2, "voidExecutionMode");
            return (Criteria) this;
        }

        public Criteria andVoidMessageIsNull() {
            addCriterion("void_message is null");
            return (Criteria) this;
        }

        public Criteria andVoidMessageIsNotNull() {
            addCriterion("void_message is not null");
            return (Criteria) this;
        }

        public Criteria andVoidMessageEqualTo(String value) {
            addCriterion("void_message =", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageNotEqualTo(String value) {
            addCriterion("void_message <>", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageGreaterThan(String value) {
            addCriterion("void_message >", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageGreaterThanOrEqualTo(String value) {
            addCriterion("void_message >=", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageLessThan(String value) {
            addCriterion("void_message <", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageLessThanOrEqualTo(String value) {
            addCriterion("void_message <=", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageLike(String value) {
            addCriterion("void_message like", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageNotLike(String value) {
            addCriterion("void_message not like", value, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageIn(List<String> values) {
            addCriterion("void_message in", values, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageNotIn(List<String> values) {
            addCriterion("void_message not in", values, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageBetween(String value1, String value2) {
            addCriterion("void_message between", value1, value2, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andVoidMessageNotBetween(String value1, String value2) {
            addCriterion("void_message not between", value1, value2, "voidMessage");
            return (Criteria) this;
        }

        public Criteria andProviderIsNull() {
            addCriterion("provider is null");
            return (Criteria) this;
        }

        public Criteria andProviderIsNotNull() {
            addCriterion("provider is not null");
            return (Criteria) this;
        }

        public Criteria andProviderEqualTo(String value) {
            addCriterion("provider =", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderNotEqualTo(String value) {
            addCriterion("provider <>", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderGreaterThan(String value) {
            addCriterion("provider >", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderGreaterThanOrEqualTo(String value) {
            addCriterion("provider >=", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderLessThan(String value) {
            addCriterion("provider <", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderLessThanOrEqualTo(String value) {
            addCriterion("provider <=", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderLike(String value) {
            addCriterion("provider like", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderNotLike(String value) {
            addCriterion("provider not like", value, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderIn(List<String> values) {
            addCriterion("provider in", values, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderNotIn(List<String> values) {
            addCriterion("provider not in", values, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderBetween(String value1, String value2) {
            addCriterion("provider between", value1, value2, "provider");
            return (Criteria) this;
        }

        public Criteria andProviderNotBetween(String value1, String value2) {
            addCriterion("provider not between", value1, value2, "provider");
            return (Criteria) this;
        }

        public Criteria andExternalCodeIsNull() {
            addCriterion("external_code is null");
            return (Criteria) this;
        }

        public Criteria andExternalCodeIsNotNull() {
            addCriterion("external_code is not null");
            return (Criteria) this;
        }

        public Criteria andExternalCodeEqualTo(String value) {
            addCriterion("external_code =", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeNotEqualTo(String value) {
            addCriterion("external_code <>", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeGreaterThan(String value) {
            addCriterion("external_code >", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeGreaterThanOrEqualTo(String value) {
            addCriterion("external_code >=", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeLessThan(String value) {
            addCriterion("external_code <", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeLessThanOrEqualTo(String value) {
            addCriterion("external_code <=", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeLike(String value) {
            addCriterion("external_code like", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeNotLike(String value) {
            addCriterion("external_code not like", value, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeIn(List<String> values) {
            addCriterion("external_code in", values, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeNotIn(List<String> values) {
            addCriterion("external_code not in", values, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeBetween(String value1, String value2) {
            addCriterion("external_code between", value1, value2, "externalCode");
            return (Criteria) this;
        }

        public Criteria andExternalCodeNotBetween(String value1, String value2) {
            addCriterion("external_code not between", value1, value2, "externalCode");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusIsNull() {
            addCriterion("document_status is null");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusIsNotNull() {
            addCriterion("document_status is not null");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusEqualTo(String value) {
            addCriterion("document_status =", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusNotEqualTo(String value) {
            addCriterion("document_status <>", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusGreaterThan(String value) {
            addCriterion("document_status >", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusGreaterThanOrEqualTo(String value) {
            addCriterion("document_status >=", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusLessThan(String value) {
            addCriterion("document_status <", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusLessThanOrEqualTo(String value) {
            addCriterion("document_status <=", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusLike(String value) {
            addCriterion("document_status like", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusNotLike(String value) {
            addCriterion("document_status not like", value, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusIn(List<String> values) {
            addCriterion("document_status in", values, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusNotIn(List<String> values) {
            addCriterion("document_status not in", values, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusBetween(String value1, String value2) {
            addCriterion("document_status between", value1, value2, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andDocumentStatusNotBetween(String value1, String value2) {
            addCriterion("document_status not between", value1, value2, "documentStatus");
            return (Criteria) this;
        }

        public Criteria andLastOperationIsNull() {
            addCriterion("last_operation is null");
            return (Criteria) this;
        }

        public Criteria andLastOperationIsNotNull() {
            addCriterion("last_operation is not null");
            return (Criteria) this;
        }

        public Criteria andLastOperationEqualTo(String value) {
            addCriterion("last_operation =", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationNotEqualTo(String value) {
            addCriterion("last_operation <>", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationGreaterThan(String value) {
            addCriterion("last_operation >", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationGreaterThanOrEqualTo(String value) {
            addCriterion("last_operation >=", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationLessThan(String value) {
            addCriterion("last_operation <", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationLessThanOrEqualTo(String value) {
            addCriterion("last_operation <=", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationLike(String value) {
            addCriterion("last_operation like", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationNotLike(String value) {
            addCriterion("last_operation not like", value, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationIn(List<String> values) {
            addCriterion("last_operation in", values, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationNotIn(List<String> values) {
            addCriterion("last_operation not in", values, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationBetween(String value1, String value2) {
            addCriterion("last_operation between", value1, value2, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOperationNotBetween(String value1, String value2) {
            addCriterion("last_operation not between", value1, value2, "lastOperation");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeIsNull() {
            addCriterion("last_outcome is null");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeIsNotNull() {
            addCriterion("last_outcome is not null");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeEqualTo(String value) {
            addCriterion("last_outcome =", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeNotEqualTo(String value) {
            addCriterion("last_outcome <>", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeGreaterThan(String value) {
            addCriterion("last_outcome >", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeGreaterThanOrEqualTo(String value) {
            addCriterion("last_outcome >=", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeLessThan(String value) {
            addCriterion("last_outcome <", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeLessThanOrEqualTo(String value) {
            addCriterion("last_outcome <=", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeLike(String value) {
            addCriterion("last_outcome like", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeNotLike(String value) {
            addCriterion("last_outcome not like", value, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeIn(List<String> values) {
            addCriterion("last_outcome in", values, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeNotIn(List<String> values) {
            addCriterion("last_outcome not in", values, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeBetween(String value1, String value2) {
            addCriterion("last_outcome between", value1, value2, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastOutcomeNotBetween(String value1, String value2) {
            addCriterion("last_outcome not between", value1, value2, "lastOutcome");
            return (Criteria) this;
        }

        public Criteria andLastMessageIsNull() {
            addCriterion("last_message is null");
            return (Criteria) this;
        }

        public Criteria andLastMessageIsNotNull() {
            addCriterion("last_message is not null");
            return (Criteria) this;
        }

        public Criteria andLastMessageEqualTo(String value) {
            addCriterion("last_message =", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageNotEqualTo(String value) {
            addCriterion("last_message <>", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageGreaterThan(String value) {
            addCriterion("last_message >", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageGreaterThanOrEqualTo(String value) {
            addCriterion("last_message >=", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageLessThan(String value) {
            addCriterion("last_message <", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageLessThanOrEqualTo(String value) {
            addCriterion("last_message <=", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageLike(String value) {
            addCriterion("last_message like", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageNotLike(String value) {
            addCriterion("last_message not like", value, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageIn(List<String> values) {
            addCriterion("last_message in", values, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageNotIn(List<String> values) {
            addCriterion("last_message not in", values, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageBetween(String value1, String value2) {
            addCriterion("last_message between", value1, value2, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andLastMessageNotBetween(String value1, String value2) {
            addCriterion("last_message not between", value1, value2, "lastMessage");
            return (Criteria) this;
        }

        public Criteria andOperationVersionIsNull() {
            addCriterion("operation_version is null");
            return (Criteria) this;
        }

        public Criteria andOperationVersionIsNotNull() {
            addCriterion("operation_version is not null");
            return (Criteria) this;
        }

        public Criteria andOperationVersionEqualTo(Long value) {
            addCriterion("operation_version =", value, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionNotEqualTo(Long value) {
            addCriterion("operation_version <>", value, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionGreaterThan(Long value) {
            addCriterion("operation_version >", value, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionGreaterThanOrEqualTo(Long value) {
            addCriterion("operation_version >=", value, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionLessThan(Long value) {
            addCriterion("operation_version <", value, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionLessThanOrEqualTo(Long value) {
            addCriterion("operation_version <=", value, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionIn(List<Long> values) {
            addCriterion("operation_version in", values, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionNotIn(List<Long> values) {
            addCriterion("operation_version not in", values, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionBetween(Long value1, Long value2) {
            addCriterion("operation_version between", value1, value2, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andOperationVersionNotBetween(Long value1, Long value2) {
            addCriterion("operation_version not between", value1, value2, "operationVersion");
            return (Criteria) this;
        }

        public Criteria andCheckedAtIsNull() {
            addCriterion("checked_at is null");
            return (Criteria) this;
        }

        public Criteria andCheckedAtIsNotNull() {
            addCriterion("checked_at is not null");
            return (Criteria) this;
        }

        public Criteria andCheckedAtEqualTo(LocalDateTime value) {
            addCriterion("checked_at =", value, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtNotEqualTo(LocalDateTime value) {
            addCriterion("checked_at <>", value, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtGreaterThan(LocalDateTime value) {
            addCriterion("checked_at >", value, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtGreaterThanOrEqualTo(LocalDateTime value) {
            addCriterion("checked_at >=", value, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtLessThan(LocalDateTime value) {
            addCriterion("checked_at <", value, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtLessThanOrEqualTo(LocalDateTime value) {
            addCriterion("checked_at <=", value, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtIn(List<LocalDateTime> values) {
            addCriterion("checked_at in", values, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtNotIn(List<LocalDateTime> values) {
            addCriterion("checked_at not in", values, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("checked_at between", value1, value2, "checkedAt");
            return (Criteria) this;
        }

        public Criteria andCheckedAtNotBetween(LocalDateTime value1, LocalDateTime value2) {
            addCriterion("checked_at not between", value1, value2, "checkedAt");
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