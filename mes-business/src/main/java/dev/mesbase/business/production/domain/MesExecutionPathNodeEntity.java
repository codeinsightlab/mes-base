// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

public class MesExecutionPathNodeEntity {
    private Long id;

    private Long executionId;

    private Integer sequenceNo;

    private Long routingNodeId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
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