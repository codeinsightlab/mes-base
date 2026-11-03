package dev.mesbase.business.production.domain;

public class MesExecutionPathNodeEntity {
    private Long id;

    private String executionCode;

    private Integer sequenceNo;

    private Long routingNodeId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExecutionCode() {
        return executionCode;
    }

    public void setExecutionCode(String executionCode) {
        this.executionCode = executionCode;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public Long getRoutingNodeId() {
        return routingNodeId;
    }

    public void setRoutingNodeId(Long routingNodeId) {
        this.routingNodeId = routingNodeId;
    }
}