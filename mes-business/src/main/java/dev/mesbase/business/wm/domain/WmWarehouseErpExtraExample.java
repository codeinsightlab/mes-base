// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.domain;

import java.util.ArrayList;
import java.util.List;

public class WmWarehouseErpExtraExample {
    protected String orderByClause;

    protected List<Criteria> oredCriteria;

    public WmWarehouseErpExtraExample() {
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

        public Criteria andExternalIdIsNull() {
            addCriterion("external_id is null");
            return (Criteria) this;
        }

        public Criteria andExternalIdIsNotNull() {
            addCriterion("external_id is not null");
            return (Criteria) this;
        }

        public Criteria andExternalIdEqualTo(String value) {
            addCriterion("external_id =", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdNotEqualTo(String value) {
            addCriterion("external_id <>", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdGreaterThan(String value) {
            addCriterion("external_id >", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdGreaterThanOrEqualTo(String value) {
            addCriterion("external_id >=", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdLessThan(String value) {
            addCriterion("external_id <", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdLessThanOrEqualTo(String value) {
            addCriterion("external_id <=", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdLike(String value) {
            addCriterion("external_id like", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdNotLike(String value) {
            addCriterion("external_id not like", value, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdIn(List<String> values) {
            addCriterion("external_id in", values, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdNotIn(List<String> values) {
            addCriterion("external_id not in", values, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdBetween(String value1, String value2) {
            addCriterion("external_id between", value1, value2, "externalId");
            return (Criteria) this;
        }

        public Criteria andExternalIdNotBetween(String value1, String value2) {
            addCriterion("external_id not between", value1, value2, "externalId");
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

        public Criteria andBindingKeyIsNull() {
            addCriterion("binding_key is null");
            return (Criteria) this;
        }

        public Criteria andBindingKeyIsNotNull() {
            addCriterion("binding_key is not null");
            return (Criteria) this;
        }

        public Criteria andBindingKeyEqualTo(String value) {
            addCriterion("binding_key =", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyNotEqualTo(String value) {
            addCriterion("binding_key <>", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyGreaterThan(String value) {
            addCriterion("binding_key >", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyGreaterThanOrEqualTo(String value) {
            addCriterion("binding_key >=", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyLessThan(String value) {
            addCriterion("binding_key <", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyLessThanOrEqualTo(String value) {
            addCriterion("binding_key <=", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyLike(String value) {
            addCriterion("binding_key like", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyNotLike(String value) {
            addCriterion("binding_key not like", value, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyIn(List<String> values) {
            addCriterion("binding_key in", values, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyNotIn(List<String> values) {
            addCriterion("binding_key not in", values, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyBetween(String value1, String value2) {
            addCriterion("binding_key between", value1, value2, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andBindingKeyNotBetween(String value1, String value2) {
            addCriterion("binding_key not between", value1, value2, "bindingKey");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionIsNull() {
            addCriterion("configuration_revision is null");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionIsNotNull() {
            addCriterion("configuration_revision is not null");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionEqualTo(Long value) {
            addCriterion("configuration_revision =", value, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionNotEqualTo(Long value) {
            addCriterion("configuration_revision <>", value, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionGreaterThan(Long value) {
            addCriterion("configuration_revision >", value, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionGreaterThanOrEqualTo(Long value) {
            addCriterion("configuration_revision >=", value, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionLessThan(Long value) {
            addCriterion("configuration_revision <", value, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionLessThanOrEqualTo(Long value) {
            addCriterion("configuration_revision <=", value, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionIn(List<Long> values) {
            addCriterion("configuration_revision in", values, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionNotIn(List<Long> values) {
            addCriterion("configuration_revision not in", values, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionBetween(Long value1, Long value2) {
            addCriterion("configuration_revision between", value1, value2, "configurationRevision");
            return (Criteria) this;
        }

        public Criteria andConfigurationRevisionNotBetween(Long value1, Long value2) {
            addCriterion("configuration_revision not between", value1, value2, "configurationRevision");
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