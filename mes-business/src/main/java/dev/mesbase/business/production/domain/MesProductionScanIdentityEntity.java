// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.time.LocalDateTime;

public class MesProductionScanIdentityEntity {
    private String id;

    private String encodedCode;

    private Long executionId;

    private LocalDateTime createdAt;

    private String productCode;

    private String materialCode;

    private String materialLotCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEncodedCode() {
        return encodedCode;
    }

    public void setEncodedCode(String encodedCode) {
        this.encodedCode = encodedCode;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialLotCode() {
        return materialLotCode;
    }

    public void setMaterialLotCode(String materialLotCode) {
        this.materialLotCode = materialLotCode;
    }
}