package dev.mesbase.business.basic.domain;

public class UnitMeasureEntity {
    private Long id;

    private String code;

    private String name;

    private String symbol;

    private String status;

    private Integer mustBeWholeNumber;

    private Integer quantityPrecision;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMustBeWholeNumber() { return mustBeWholeNumber; }

    public void setMustBeWholeNumber(Integer mustBeWholeNumber) { this.mustBeWholeNumber = mustBeWholeNumber; }

    public Integer getQuantityPrecision() { return quantityPrecision; }

    public void setQuantityPrecision(Integer quantityPrecision) { this.quantityPrecision = quantityPrecision; }
}
