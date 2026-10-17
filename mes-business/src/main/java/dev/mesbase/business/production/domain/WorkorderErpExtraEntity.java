// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

public class WorkorderErpExtraEntity {
    private Long id;

    private String externalId;

    private String externalCode;

    private String provider;

    private String bindingKey;

    private Long configurationRevision;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public void setExternalCode(String externalCode) {
        this.externalCode = externalCode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBindingKey() {
        return bindingKey;
    }

    public void setBindingKey(String bindingKey) {
        this.bindingKey = bindingKey;
    }

    public Long getConfigurationRevision() {
        return configurationRevision;
    }

    public void setConfigurationRevision(Long configurationRevision) {
        this.configurationRevision = configurationRevision;
    }
}