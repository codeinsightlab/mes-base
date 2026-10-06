package dev.mesbase.mes.pro.personnelanalysis.domain;
@lombok.Data public class PersonnelRecordVO {
 private Long recordId,userId;private java.time.LocalDateTime createTime;
 private String userName,createBy,reportingWorkType,processCode,processName,itemCode,itemName,unitOfMeasure;
 private java.math.BigDecimal reportingWorkQuantity;
}
