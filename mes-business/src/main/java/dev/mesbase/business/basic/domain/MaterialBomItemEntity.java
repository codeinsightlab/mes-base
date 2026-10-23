// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.basic.domain;

import java.math.BigDecimal;

public class MaterialBomItemEntity {
    private Long id;

    private Long bomId;

    private Long materialId;

    private BigDecimal quantity;

    private Long unitId;

    private Long childBomId;

    private Integer sort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBomId() {
        return bomId;
    }

    public void setBomId(Long bomId) {
        this.bomId = bomId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Long getChildBomId() {
        return childBomId;
    }

    public void setChildBomId(Long childBomId) {
        this.childBomId = childBomId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
