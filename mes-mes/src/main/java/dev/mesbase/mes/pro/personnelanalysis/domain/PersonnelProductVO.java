package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PersonnelProductVO {
    private String itemCode;
    private String itemName;
    private Long activeMonths;
    private Long processCount;
    private Long reportRecordCount;
    private LocalDateTime lastReportTime;
}
